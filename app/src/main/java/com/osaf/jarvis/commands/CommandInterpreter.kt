package com.osaf.jarvis.commands

import com.osaf.jarvis.ai.OfflineAiEngine
import com.osaf.jarvis.database.MemoryRepository
import com.osaf.jarvis.database.SettingsRepository
import com.osaf.jarvis.device.DeviceActionResult
import com.osaf.jarvis.device.DeviceController
import com.osaf.jarvis.notifications.NotificationHelper
import com.osaf.jarvis.utils.LanguageDetector
import com.osaf.jarvis.utils.LanguageMode
import com.osaf.jarvis.utils.MathEvaluator
import com.osaf.jarvis.utils.MathResult
import android.content.Context
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class PendingConfirmation {
    data class Call(val target: String, val displayLabel: String) : PendingConfirmation()
    data class Sms(val recipient: String, val body: String) : PendingConfirmation()
}

sealed class CommandOutcome {
    data class Handled(
        val responseText: String,
        val language: LanguageMode,
        val source: String = "command",
        val confirmation: PendingConfirmation? = null
    ) : CommandOutcome()

    data class NeedsOnlineAi(
        val cleanedQuery: String,
        val language: LanguageMode
    ) : CommandOutcome()
}

class CommandInterpreter(
    private val context: Context,
    private val deviceController: DeviceController,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
    private val offlineAiEngine: OfflineAiEngine
) {

    fun stripWakePhrase(raw: String, assistantName: String): String {
        val escapedName = Regex.escape(assistantName.trim().ifEmpty { "JARVIS" })
        val pattern = Regex(
            """^(?:hey\s+$escapedName|ok\s+$escapedName|hi\s+$escapedName|hello\s+$escapedName|$escapedName|hey\s+jarvis|jarvis)\s*[,:\-!\s]*""",
            RegexOption.IGNORE_CASE
        )
        return raw.trim().replaceFirst(pattern, "").trim()
    }

    suspend fun interpretAndExecute(rawInput: String): CommandOutcome {
        val settings = settingsRepository.settingsFlow.first()
        val user = settings.userDisplayName
        val assistant = settings.assistantName
        val cleaned = stripWakePhrase(rawInput, assistant)
        val lang = LanguageDetector.detect(rawInput, settings.preferredLanguage)

        // If user only said the wake phrase
        if (cleaned.isBlank()) {
            return CommandOutcome.Handled(
                responseText = greetingText(user, lang, intro = true),
                language = lang
            )
        }

        val lower = cleaned.lowercase().trim()

        // 1. Greetings / Identity
        if (matchesGreeting(lower)) {
            return CommandOutcome.Handled(
                responseText = greetingText(user, lang, intro = true),
                language = lang
            )
        }
        if (matchesCapabilities(lower)) {
            return CommandOutcome.Handled(
                responseText = offlineAiEngine.buildCapabilitiesResponse(user, assistant, lang),
                language = lang
            )
        }

        // 2. Mode switching (Offline / Online)
        if (containsAny(lower, listOf("offline mode", "use offline", "go offline", "offline chalao", "offline use karo"))) {
            settingsRepository.setPreferOnlineMode(false)
            val msg = when (lang) {
                LanguageMode.URDU -> "جی $user، آف لائن موڈ فعال کر دیا گیا ہے۔"
                LanguageMode.ROMAN_URDU -> "Ji $user, Offline Mode activate kar diya gaya hai."
                else -> "Understood, $user. Switching to Offline Mode."
            }
            return CommandOutcome.Handled(msg, lang)
        }
        if (containsAny(lower, listOf("online mode", "use online", "go online", "online chalao", "online use karo"))) {
            settingsRepository.setPreferOnlineMode(true)
            val msg = when (lang) {
                LanguageMode.URDU -> "جی $user، آن لائن موڈ فعال کر دیا گیا ہے۔"
                LanguageMode.ROMAN_URDU -> "Ji $user, Online Mode activate kar diya gaya hai."
                else -> "Understood, $user. Online AI Mode is now enabled."
            }
            return CommandOutcome.Handled(msg, lang)
        }

        // 3. Flashlight Control
        if (containsAny(lower, listOf("flashlight", "torch", "flash light")) ||
            (lower.contains("light") && containsAny(lower, listOf("on", "off", "kholo", "band", "chalu")))
        ) {
            val turnOn = containsAny(lower, listOf("on", "enable", "start", "chalu", "jalao", "kholo"))
            val turnOff = containsAny(lower, listOf("off", "disable", "stop", "band", "bujhao"))
            return when {
                turnOn && !turnOff -> formatDeviceResult(deviceController.setFlashlight(true), lang, user)
                turnOff && !turnOn -> formatDeviceResult(deviceController.setFlashlight(false), lang, user)
                else -> {
                    val next = !deviceController.isFlashlightOn.value
                    formatDeviceResult(deviceController.setFlashlight(next), lang, user)
                }
            }
        }

        // 4. Battery Status
        if (containsAny(lower, listOf("battery", "charging", "bettery"))) {
            val status = deviceController.getBatteryStatus()
            val msg = when (lang) {
                LanguageMode.URDU -> "$user، بیٹری اس وقت ${status.percentage}% ہے (${status.statusLabel})۔"
                LanguageMode.ROMAN_URDU -> "$user, is waqt battery ${status.percentage}% hai (${status.statusLabel})."
                else -> "$user, battery is currently at ${status.percentage}% (${status.statusLabel})."
            }
            return CommandOutcome.Handled(msg, lang)
        }

        // 5. Date and Time
        if (containsAny(lower, listOf("date", "tareekh", "tarikh", "today", "aaj ki")) &&
            !lower.contains("update")
        ) {
            val (date, time) = deviceController.getCurrentDateTimeFormatted()
            val msg = when (lang) {
                LanguageMode.URDU -> "$user، آج کی تاریخ $date ہے اور وقت $time ہے۔"
                LanguageMode.ROMAN_URDU -> "$user, aaj ki tareekh $date hai aur waqt $time hai."
                else -> "$user, today is $date, and the current time is $time."
            }
            return CommandOutcome.Handled(msg, lang)
        }
        if (containsAny(lower, listOf("what time", "current time", "time kya", "waqt kya", "kitne baje", "tell me the time"))) {
            val (_, time) = deviceController.getCurrentDateTimeFormatted()
            val msg = when (lang) {
                LanguageMode.URDU -> "$user، اس وقت $time بجے ہیں۔"
                LanguageMode.ROMAN_URDU -> "$user, is waqt $time ho rahe hain."
                else -> "$user, the current time is $time."
            }
            return CommandOutcome.Handled(msg, lang)
        }

        // 6. Wi-Fi & Bluetooth Settings
        if (containsAny(lower, listOf("wifi", "wi-fi"))) {
            return formatDeviceResult(deviceController.openWifiSettings(), lang, user)
        }
        if (lower.contains("bluetooth")) {
            return formatDeviceResult(deviceController.openBluetoothSettings(), lang, user)
        }

        // 7. Media Volume Control
        if (lower.contains("volume") || lower.contains("awaaz") || lower.contains("awaz")) {
            val pctMatch = Regex("""(\d{1,3})\s*%?""").find(lower)
            if (pctMatch != null) {
                val pct = pctMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: 50
                return formatDeviceResult(deviceController.setMediaVolumePercent(pct), lang, user)
            }
            if (containsAny(lower, listOf("up", "increase", "raise", "high", "barhao", "zyada", "ziyada", "tez"))) {
                return formatDeviceResult(deviceController.adjustMediaVolume(raise = true), lang, user)
            }
            if (containsAny(lower, listOf("down", "decrease", "lower", "low", "kam", "slow"))) {
                return formatDeviceResult(deviceController.adjustMediaVolume(raise = false), lang, user)
            }
            val current = deviceController.getMediaVolumePercent()
            return CommandOutcome.Handled("$user, current media volume is $current%.", lang)
        }

        // 8. Timer
        if (lower.contains("timer")) {
            val seconds = parseDurationSeconds(lower)
            return if (seconds != null && seconds > 0) {
                formatDeviceResult(deviceController.setSystemTimer(seconds), lang, user)
            } else {
                val clarify = when (lang) {
                    LanguageMode.ROMAN_URDU -> "$user, kitne minute ya second ka timer lagana hai?"
                    LanguageMode.URDU -> "$user، کتنے منٹ یا سیکنڈ کا ٹائمر لگانا ہے؟"
                    else -> "$user, how long should I set the timer for?"
                }
                CommandOutcome.Handled(clarify, lang)
            }
        }

        // 9. Alarm
        if (lower.contains("alarm")) {
            val parsedTime = parseAlarmHourMinute(lower)
            return if (parsedTime != null) {
                formatDeviceResult(
                    deviceController.setSystemAlarm(parsedTime.first, parsedTime.second),
                    lang,
                    user
                )
            } else {
                val clarify = when (lang) {
                    LanguageMode.ROMAN_URDU -> "$user, kis waqt ka alarm lagana hai? Masalan: subah 7 baje."
                    LanguageMode.URDU -> "$user، کس وقت کا الارم لگانا ہے؟"
                    else -> "$user, what time should I set the alarm for? For example, 7:00 AM."
                }
                CommandOutcome.Handled(clarify, lang)
            }
        }

        // 10. Google Search & Open Website
        val googleMatch = extractSearchQuery(cleaned)
        if (googleMatch != null) {
            return formatDeviceResult(deviceController.openGoogleSearch(googleMatch), lang, user)
        }
        val websiteMatch = extractWebsiteUrl(cleaned)
        if (websiteMatch != null) {
            return formatDeviceResult(deviceController.openWebsite(websiteMatch), lang, user)
        }

        // 11. Local Reminders
        val reminderParsed = parseReminderCommand(cleaned)
        if (reminderParsed != null) {
            val (title, triggerAt) = reminderParsed
            val id = memoryRepository.addReminder(title, triggerAt)
            NotificationHelper.scheduleLocalReminder(context, id, title, triggerAt)
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(triggerAt))
            val msg = when (lang) {
                LanguageMode.URDU -> "$user، ریمائنڈر محفوظ کر لیا گیا ہے: \"$title\" ($timeStr)۔"
                LanguageMode.ROMAN_URDU -> "$user, reminder save kar liya gaya hai: \"$title\" ($timeStr)."
                else -> "$user, I have saved your reminder: \"$title\" for $timeStr."
            }
            return CommandOutcome.Handled(msg, lang)
        }

        // 12. Save / Read Notes
        if (isSaveNoteIntent(lower)) {
            val noteBody = extractNoteContent(cleaned)
            if (noteBody.isBlank()) {
                val clarify = when (lang) {
                    LanguageMode.URDU -> "$user، براہ کرم بتائیں کہ نوٹ میں کیا محفوظ کرنا ہے؟"
                    LanguageMode.ROMAN_URDU -> "$user, note mein kya likhna hai? Bata dein taake main save kar doon."
                    else -> "$user, what would you like me to save in the note?"
                }
                return CommandOutcome.Handled(clarify, lang)
            }
            val title = noteBody.split(" ").take(5).joinToString(" ")
            memoryRepository.addNote(title = title, content = noteBody, category = "Voice Note", language = lang.code)
            val msg = when (lang) {
                LanguageMode.URDU -> "$user، آپ کا نوٹ محفوظ کر لیا گیا ہے: \"$noteBody\"۔"
                LanguageMode.ROMAN_URDU -> "$user, aapka note save kar liya gaya hai: \"$noteBody\"."
                else -> "$user, note saved to local memory: \"$noteBody\"."
            }
            return CommandOutcome.Handled(msg, lang)
        }
        if (containsAny(lower, listOf("show notes", "my notes", "read notes", "mere notes", "notes dikhao"))) {
            val notes = memoryRepository.getNotesSnapshot()
            val msg = if (notes.isEmpty()) {
                "$user, you currently have no saved notes."
            } else {
                val preview = notes.take(4).joinToString("\n") { "• ${it.title}: ${it.content}" }
                "$user, here are your latest saved notes:\n$preview"
            }
            return CommandOutcome.Handled(msg, lang)
        }

        // 13. Confirmation-Gated Calls & SMS
        val callTarget = extractCallTarget(cleaned)
        if (callTarget != null) {
            val msg = when (lang) {
                LanguageMode.ROMAN_URDU -> "$user, kya main $callTarget par call dialer open karoon? Confirm karein."
                LanguageMode.URDU -> "$user، کیا میں $callTarget پر کال کرنے کے لیے ڈائلر کھولوں؟"
                else -> "$user, please confirm if you want me to open the dialer for $callTarget."
            }
            return CommandOutcome.Handled(
                responseText = msg,
                language = lang,
                confirmation = PendingConfirmation.Call(callTarget, callTarget)
            )
        }
        val smsTarget = extractSmsTarget(cleaned)
        if (smsTarget != null) {
            val msg = "$user, please confirm before opening SMS composer for ${smsTarget.first}."
            return CommandOutcome.Handled(
                responseText = msg,
                language = lang,
                confirmation = PendingConfirmation.Sms(smsTarget.first, smsTarget.second)
            )
        }

        // 14. Open Installed App / Camera / Settings
        val appTarget = extractAppToOpen(cleaned)
        if (appTarget != null) {
            return formatDeviceResult(deviceController.launchAppByQuery(appTarget), lang, user)
        }

        // 15. Mathematical Calculations
        if (MathEvaluator.looksLikeMath(cleaned)) {
            return when (val result = MathEvaluator.evaluate(cleaned)) {
                is MathResult.Success -> {
                    val msg = when (lang) {
                        LanguageMode.URDU -> "$user، جواب ${result.formatted} ہے۔"
                        LanguageMode.ROMAN_URDU -> "$user, iska jawab ${result.formatted} hai."
                        else -> "$user, the answer is ${result.formatted}."
                    }
                    CommandOutcome.Handled(msg, lang)
                }
                is MathResult.Error -> {
                    CommandOutcome.Handled("$user, I could not calculate that: ${result.reason}", lang)
                }
            }
        }

        // 16. Fallback to Online AI (or honest Offline reply)
        return CommandOutcome.NeedsOnlineAi(cleaned, lang)
    }

    private fun greetingText(user: String, lang: LanguageMode, intro: Boolean): String {
        return when (lang) {
            LanguageMode.URDU -> "السلام علیکم $user، میں OSAF JARVIS ہوں۔ میں آپ کی کیا مدد کر سکتا ہوں؟"
            LanguageMode.ROMAN_URDU -> "Assalam-o-Alaikum $user, main OSAF JARVIS hoon. Batayein main aapki kya madad kar sakta hoon?"
            else -> if (intro) "Hello $user, I am OSAF JARVIS. How may I assist you?" else "At your service, $user."
        }
    }

    private fun formatDeviceResult(res: DeviceActionResult, lang: LanguageMode, user: String): CommandOutcome.Handled {
        return when (res) {
            is DeviceActionResult.Success -> CommandOutcome.Handled("$user, ${res.message}", lang)
            is DeviceActionResult.Error -> CommandOutcome.Handled("$user, ${res.message}", lang)
        }
    }

    private fun matchesGreeting(lower: String): Boolean {
        val exact = setOf(
            "hello", "hi", "hey", "salam", "assalam o alaikum", "assalamualaikum",
            "good morning", "good evening", "good afternoon", "who are you", "introduce yourself"
        )
        return lower in exact
    }

    private fun matchesCapabilities(lower: String): Boolean {
        return containsAny(lower, listOf("what can you do", "help", "kya kar sakte ho", "capabilities", "commands"))
    }

    private fun parseDurationSeconds(lower: String): Int? {
        val regex = Regex("""(\d+)\s*(hours?|hrs?|ghante?|minutes?|mins?|min|seconds?|secs?|sec)""")
        val matches = regex.findAll(lower).toList()
        if (matches.isEmpty()) return null
        var total = 0
        for (m in matches) {
            val value = m.groupValues[1].toIntOrNull() ?: continue
            val unit = m.groupValues[2]
            total += when {
                unit.startsWith("h") || unit.startsWith("gh") -> value * 3600
                unit.startsWith("m") -> value * 60
                else -> value
            }
        }
        return total.takeIf { it > 0 }
    }

    private fun parseAlarmHourMinute(lower: String): Pair<Int, Int>? {
        val timeRegex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|subah|shaam|raat|baje)?""")
        // Find a numeric match that isn't just random
        for (m in timeRegex.findAll(lower)) {
            val rawHour = m.groupValues[1].toIntOrNull() ?: continue
            if (rawHour > 23) continue
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            if (minute > 59) continue
            val suffix = m.groupValues[3]
            val isPm = suffix == "pm" || suffix == "shaam" || suffix == "raat" ||
                lower.contains("pm") || lower.contains("shaam") || lower.contains("raat")
            val isAm = suffix == "am" || suffix == "subah" || lower.contains("am") || lower.contains("subah")
            val hour24 = when {
                isPm && rawHour < 12 -> rawHour + 12
                isAm && rawHour == 12 -> 0
                else -> rawHour
            }
            return hour24 to minute
        }
        return null
    }

    private fun extractSearchQuery(cleaned: String): String? {
        val patterns = listOf(
            Regex("""(?:google\s+(?:par|pe|on)\s+)(.+?)(?:\s+search\s+karo|\s+search)?$""", RegexOption.IGNORE_CASE),
            Regex("""(?:search\s+(?:on\s+google\s+for|google\s+for|for)?\s+)(.+)$""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(cleaned)
            if (m != null) {
                val q = m.groupValues[1].trim()
                    .removeSuffix("search karo")
                    .removeSuffix("search")
                    .trim()
                if (q.isNotBlank()) return q
            }
        }
        return null
    }

    private fun extractWebsiteUrl(cleaned: String): String? {
        val urlRegex = Regex("""\b((?:https?://)?[a-zA-Z0-9\-]+\.[a-zA-Z]{2,}(?:/[^\s]*)?)\b""")
        val lower = cleaned.lowercase()
        if (lower.startsWith("open ") || lower.contains("website") || lower.contains("kholo")) {
            val m = urlRegex.find(cleaned)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private fun parseReminderCommand(cleaned: String): Pair<String, Long>? {
        val lower = cleaned.lowercase()
        if (!containsAny(lower, listOf("remind", "reminder", "yaad dilana", "yaad dilao"))) return null
        val durationSec = parseDurationSeconds(lower) ?: (10 * 60)
        val triggerAt = System.currentTimeMillis() + durationSec * 1000L
        val title = cleaned
            .replace(Regex("""(?i)remind\s+me\s+(?:in\s+\d+\s+\w+\s*)?(?:to\s+)?"""), "")
            .replace(Regex("""(?i)\d+\s*(?:minutes?|mins?|hours?|seconds?)\s*(?:baad|ka)?\s*(?:yaad\s+dilana|yaad\s+dilao|reminder)?"""), "")
            .trim()
            .ifEmpty { "Scheduled reminder" }
        return title to triggerAt
    }

    private fun isSaveNoteIntent(lower: String): Boolean {
        return containsAny(
            lower,
            listOf("save note", "ek note save", "note save karo", "take a note", "add note", "note likho", "save a note")
        )
    }

    private fun extractNoteContent(cleaned: String): String {
        val stripped = cleaned
            .replace(Regex("""(?i)^(?:please\s+)?(?:ek\s+)?(?:save\s+a?\s*note|note\s+save\s+karo|ek\s+note\s+save\s+karo|take\s+a\s+note|add\s+note|note\s+likho)\s*(?:that|ke|ki|:|-)?\s*"""), "")
            .trim()
        return stripped
    }

    private fun extractCallTarget(cleaned: String): String? {
        val m = Regex("""(?i)^(?:call|dial|phone\s+karo|call\s+karo)\s+([+\d\-\s]{3,20}|[a-zA-Z0-9\s]{2,30})$""").find(cleaned.trim())
        return m?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractSmsTarget(cleaned: String): Pair<String, String>? {
        val m = Regex("""(?i)^(?:send\s+(?:an?\s+)?sms\s+to|text|message)\s+([+\d]{3,20}|\S+)\s*(?:saying|that|:)?\s*(.*)$""").find(cleaned.trim())
            ?: return null
        val recipient = m.groupValues[1].trim()
        val body = m.groupValues[2].trim()
        return recipient to body
    }

    private fun extractAppToOpen(cleaned: String): String? {
        val p1 = Regex("""(?i)^(?:open|launch|start|chalu\s+karo)\s+(.+)$""").find(cleaned.trim())
        if (p1 != null) {
            return p1.groupValues[1].trim().removeSuffix("app").trim()
        }
        val p2 = Regex("""(?i)^(.+?)\s+(?:open\s+karo|kholo|khol\s+do|chalao)$""").find(cleaned.trim())
        if (p2 != null) {
            return p2.groupValues[1].trim().removeSuffix("app").trim()
        }
        return null
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }
}
