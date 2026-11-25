package com.osaf.jarvis.commands

import android.content.Context
import com.osaf.jarvis.ai.OfflineAiEngine
import com.osaf.jarvis.ai.PersonalityEngine
import com.osaf.jarvis.ai.UserMood
import com.osaf.jarvis.database.MemoryRepository
import com.osaf.jarvis.database.SettingsRepository
import com.osaf.jarvis.device.DeviceActionResult
import com.osaf.jarvis.device.DeviceController
import com.osaf.jarvis.notifications.NotificationHelper
import com.osaf.jarvis.utils.LanguageDetector
import com.osaf.jarvis.utils.LanguageMode
import com.osaf.jarvis.utils.MathEvaluator
import com.osaf.jarvis.utils.MathResult
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
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
        val confirmation: PendingConfirmation? = null,
        val shouldEndVoiceSession: Boolean = false
    ) : CommandOutcome()

    data class NeedsOnlineAi(
        val cleanedQuery: String,
        val language: LanguageMode,
        val mood: UserMood
    ) : CommandOutcome()
}

class CommandInterpreter(
    private val context: Context,
    private val deviceController: DeviceController,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
    private val offlineAiEngine: OfflineAiEngine,
    private val personalityEngine: PersonalityEngine
) {

    fun stripWakePhrase(raw: String, assistantName: String): String {
        val escapedName = Regex.escape(assistantName.trim().ifEmpty { "JARVIS" })
        val pattern = Regex(
            """^(?:hey\s+$escapedName|ok\s+$escapedName|hi\s+$escapedName|hello\s+$escapedName|a\s+$escapedName|$escapedName|hey\s+jarvis|hi\s+jarvis|ok\s+jarvis|a\s+jarvis|jarvis|jarvish|jarves|جی\s+جاروس|جاروس)\s*[,:\-!\s]*""",
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
        val mood = personalityEngine.detectMood(cleaned)

        // Extract and persist any personal facts / preferences / projects / instructions
        val extractedFacts = personalityEngine.extractMemoryFacts(cleaned)
        if (extractedFacts.isNotEmpty()) {
            for (fact in extractedFacts) {
                memoryRepository.upsertMemoryFact(
                    category = fact.category,
                    factKey = fact.key,
                    factValue = fact.value,
                    importance = 3
                )
            }
            if (extractedFacts.any { it.key == "User Name" }) {
                val newName = extractedFacts.first { it.key == "User Name" }.value
                settingsRepository.updatePersonalization(
                    assistantName = settings.assistantName,
                    userDisplayName = newName,
                    preferredLanguage = settings.preferredLanguage,
                    themeMode = settings.themeMode
                )
            }
            val savedText = extractedFacts.joinToString(", ") { it.value }
            val reply = when (lang) {
                LanguageMode.URDU -> "جی $user، میں نے یہ یاد رکھ لیا ہے: \"$savedText\"۔"
                LanguageMode.ROMAN_URDU -> "Done $user! Maine yaad rakh liya hai: \"$savedText\"."
                else -> "Got it, $user — I'll remember that: \"$savedText\"."
            }
            memoryRepository.recordCommandUsage("memory_save", cleaned)
            return CommandOutcome.Handled(reply, lang, source = "memory")
        }

        if (cleaned.isBlank()) {
            val reply = personalityEngine.tryBuildConversationalReply("hey", user, assistant, lang, mood)
                ?: "Haan ji $user, main sun raha hoon!"
            return CommandOutcome.Handled(reply, lang, source = "personality")
        }

        val lower = cleaned.lowercase().trim()

        // 1. Personality Engine (mood-aware friendly conversation, non-repetitive)
        personalityEngine.tryBuildConversationalReply(lower, user, assistant, lang, mood)?.let { reply ->
            val endSession = lower in setOf("bye", "goodbye", "khuda hafiz", "allah hafiz", "bas", "bass", "chalo bye", "stop talking", "خدا حافظ", "اللہ حافظ")
            return CommandOutcome.Handled(reply, lang, source = "personality", shouldEndVoiceSession = endSession)
        }

        if (matchesCapabilities(lower)) {
            return CommandOutcome.Handled(
                responseText = offlineAiEngine.buildCapabilitiesResponse(user, assistant, lang),
                language = lang
            )
        }

        // 2. Recall Long-Term Memory
        if (containsAny(lower, listOf("what do you remember", "what do you know about me", "mere baare mein", "meri memory", "my memory", "show memory"))) {
            val facts = memoryRepository.getFactsSnapshot()
            val msg = if (facts.isEmpty()) {
                if (lang == LanguageMode.ROMAN_URDU) "$user, abhi maine aapke baare mein koi khaas fact save nahi kiya. Aap 'Yaad rakhna mera project...' keh kar save karwa sakte hain!"
                else "I haven't saved any long-term memory facts yet, $user. You can tell me \"Remember that my project is...\" anytime!"
            } else {
                val list = facts.take(6).joinToString("\n") { "• [${it.category}] ${it.factValue}" }
                if (lang == LanguageMode.ROMAN_URDU) "$user, mujhe yeh baatein yaad hain:\n$list"
                else "Here's what I remember about you, $user:\n$list"
            }
            return CommandOutcome.Handled(msg, lang, source = "memory")
        }

        // 3. Mode switching (Offline / Online)
        if (containsAny(lower, listOf("offline mode", "use offline", "go offline", "offline chalao", "offline use karo"))) {
            settingsRepository.setPreferOnlineMode(false)
            val msg = when (lang) {
                LanguageMode.URDU -> "جی بالکل $user، میں نے آف لائن موڈ آن کر دیا ہے۔"
                LanguageMode.ROMAN_URDU -> "Ji bilkul $user! Maine Offline Mode on kar diya hai, ab bina internet kaam chalega."
                else -> "Got it, $user! Switched over to Offline Mode for you."
            }
            return CommandOutcome.Handled(msg, lang)
        }
        if (containsAny(lower, listOf("online mode", "use online", "go online", "online chalao", "online use karo"))) {
            settingsRepository.setPreferOnlineMode(true)
            val msg = when (lang) {
                LanguageMode.URDU -> "جی $user، آن لائن AI موڈ فعال کر دیا گیا ہے!"
                LanguageMode.ROMAN_URDU -> "Done $user! Online AI Mode wapas on kar diya hai."
                else -> "All set, $user! Online AI Mode is active now."
            }
            return CommandOutcome.Handled(msg, lang)
        }

        // 4. Flashlight Control
        if (containsAny(lower, listOf("flashlight", "torch", "flash light")) ||
            (lower.contains("light") && containsAny(lower, listOf("on", "off", "kholo", "band", "chalu", "jalao")))
        ) {
            memoryRepository.recordCommandUsage("flashlight", cleaned)
            val turnOn = containsAny(lower, listOf("on", "enable", "start", "chalu", "jalao", "kholo"))
            val turnOff = containsAny(lower, listOf("off", "disable", "stop", "band", "bujhao"))
            val enable = when {
                turnOn && !turnOff -> true
                turnOff && !turnOn -> false
                else -> !deviceController.isFlashlightOn.value
            }
            return when (val res = deviceController.setFlashlight(enable)) {
                is DeviceActionResult.Success -> {
                    val reply = when (lang) {
                        LanguageMode.URDU -> if (enable) "جی $user، فلیش لائٹ آن کر دی ہے۔" else "جی $user، فلیش لائٹ بند کر دی ہے۔"
                        LanguageMode.ROMAN_URDU -> if (enable) "Lo $user, flashlight on kar di hai!" else "Theek hai $user, flashlight band kar di hai."
                        else -> if (enable) "Sure thing, $user — flashlight is on!" else "Got it, $user — flashlight is off now."
                    }
                    CommandOutcome.Handled(reply, lang)
                }
                is DeviceActionResult.Error -> CommandOutcome.Handled("Sorry $user, ${res.message}", lang)
            }
        }

        // 5. Battery Status
        if (containsAny(lower, listOf("battery", "charging", "bettery"))) {
            memoryRepository.recordCommandUsage("battery", cleaned)
            val status = deviceController.getBatteryStatus()
            val msg = when (lang) {
                LanguageMode.URDU -> "$user، اس وقت بیٹری ${status.percentage}% ہے (${status.statusLabel})۔"
                LanguageMode.ROMAN_URDU -> when {
                    status.percentage >= 70 -> "$user, battery abhi ${status.percentage}% hai (${status.statusLabel}) — kaafi time aaram se chal jayegi!"
                    status.percentage <= 20 -> "$user, battery sirf ${status.percentage}% reh gayi hai (${status.statusLabel}), charger laga lena behtar hoga."
                    else -> "$user, is waqt battery ${status.percentage}% hai (${status.statusLabel})."
                }
                else -> when {
                    status.percentage >= 70 -> "Looking good, $user! Battery is at ${status.percentage}% (${status.statusLabel})."
                    status.percentage <= 20 -> "Heads up $user — battery is down to ${status.percentage}% (${status.statusLabel}). You might want to plug in soon."
                    else -> "Your battery is at ${status.percentage}% right now ($user, ${status.statusLabel})."
                }
            }
            return CommandOutcome.Handled(msg, lang)
        }

        // 6. Date and Time
        if (containsAny(lower, listOf("date", "tareekh", "tarikh", "today", "aaj ki")) && !lower.contains("update")) {
            memoryRepository.recordCommandUsage("date_time", cleaned)
            val (date, time) = deviceController.getCurrentDateTimeFormatted()
            val msg = when (lang) {
                LanguageMode.URDU -> "$user، آج کی تاریخ $date ہے اور وقت $time ہے۔"
                LanguageMode.ROMAN_URDU -> "$user, aaj ki tareekh $date hai aur abhi $time ho rahe hain."
                else -> "Today is $date, $user — and it's $time right now."
            }
            return CommandOutcome.Handled(msg, lang)
        }
        if (containsAny(lower, listOf("what time", "current time", "time kya", "waqt kya", "kitne baje", "tell me the time"))) {
            memoryRepository.recordCommandUsage("date_time", cleaned)
            val (_, time) = deviceController.getCurrentDateTimeFormatted()
            val msg = when (lang) {
                LanguageMode.URDU -> "$user، اس وقت $time بجے ہیں۔"
                LanguageMode.ROMAN_URDU -> "$user, abhi $time ho rahe hain."
                else -> "It's $time right now, $user."
            }
            return CommandOutcome.Handled(msg, lang)
        }

        // 7. Wi-Fi & Bluetooth Settings
        if (containsAny(lower, listOf("wifi", "wi-fi"))) {
            memoryRepository.recordCommandUsage("wifi_settings", cleaned)
            return formatDeviceResult(deviceController.openWifiSettings(), lang, user)
        }
        if (lower.contains("bluetooth")) {
            memoryRepository.recordCommandUsage("bluetooth_settings", cleaned)
            return formatDeviceResult(deviceController.openBluetoothSettings(), lang, user)
        }

        // 8. Media Volume Control
        if (lower.contains("volume") || lower.contains("awaaz") || lower.contains("awaz")) {
            memoryRepository.recordCommandUsage("volume", cleaned)
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
            return CommandOutcome.Handled("Media volume is at $current% right now, $user.", lang)
        }

        // 9. Timer
        if (lower.contains("timer")) {
            memoryRepository.recordCommandUsage("timer", cleaned)
            val seconds = parseDurationSeconds(lower)
            return if (seconds != null && seconds > 0) {
                formatDeviceResult(deviceController.setSystemTimer(seconds), lang, user)
            } else {
                val clarify = when (lang) {
                    LanguageMode.ROMAN_URDU -> "$user, kitne minute ya second ka timer lagana hai? Bata dein abhi set kar deta hoon."
                    LanguageMode.URDU -> "$user، کتنے منٹ یا سیکنڈ کا ٹائمر لگانا ہے؟"
                    else -> "Sure thing, $user — how long should I set the timer for?"
                }
                CommandOutcome.Handled(clarify, lang)
            }
        }

        // 10. Alarm
        if (lower.contains("alarm")) {
            memoryRepository.recordCommandUsage("alarm", cleaned)
            val parsedTime = parseAlarmHourMinute(lower)
            return if (parsedTime != null) {
                formatDeviceResult(deviceController.setSystemAlarm(parsedTime.first, parsedTime.second), lang, user)
            } else {
                val clarify = when (lang) {
                    LanguageMode.ROMAN_URDU -> "$user, kis waqt ka alarm lagana hai? Jaise: subah 7 baje."
                    LanguageMode.URDU -> "$user، کس وقت کا الارم لگانا ہے؟"
                    else -> "What time would you like the alarm for, $user? For example, 7:00 AM."
                }
                CommandOutcome.Handled(clarify, lang)
            }
        }

        // 11. Explicit Browser Google Search & Open Website
        val googleMatch = extractBrowserSearchQuery(cleaned)
        if (googleMatch != null) {
            memoryRepository.recordCommandUsage("google_browser", cleaned)
            return formatDeviceResult(deviceController.openGoogleSearch(googleMatch), lang, user)
        }
        val websiteMatch = extractWebsiteUrl(cleaned)
        if (websiteMatch != null) {
            memoryRepository.recordCommandUsage("open_website", cleaned)
            return formatDeviceResult(deviceController.openWebsite(websiteMatch), lang, user)
        }

        // 12. Local Reminders
        val reminderParsed = parseReminderCommand(cleaned)
        if (reminderParsed != null) {
            memoryRepository.recordCommandUsage("reminder", cleaned)
            val (title, triggerAt) = reminderParsed
            val id = memoryRepository.addReminder(title, triggerAt)
            if (settings.notificationsEnabled) {
                NotificationHelper.scheduleLocalReminder(context, id, title, triggerAt)
            }
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(triggerAt))
            val msg = when (lang) {
                LanguageMode.URDU -> "جی $user، میں نے \"$title\" کا ریمائنڈر $timeStr کے لیے سیٹ کر دیا ہے!"
                LanguageMode.ROMAN_URDU -> "Done $user! Maine \"$title\" ka reminder $timeStr ke liye laga diya hai."
                else -> "Got it, $user! I'll remind you about \"$title\" at $timeStr."
            }
            return CommandOutcome.Handled(msg, lang)
        }

        // 13. Save / Read Notes
        if (isSaveNoteIntent(lower)) {
            memoryRepository.recordCommandUsage("save_note", cleaned)
            val noteBody = extractNoteContent(cleaned)
            if (noteBody.isBlank()) {
                val clarify = when (lang) {
                    LanguageMode.URDU -> "$user، بتائیے نوٹ میں کیا لکھ کر محفوظ کروں؟"
                    LanguageMode.ROMAN_URDU -> "$user, note mein kya likhna hai? Bata dein taake main save kar loon."
                    else -> "Of course, $user — what would you like me to write down in that note?"
                }
                return CommandOutcome.Handled(clarify, lang)
            }
            val title = noteBody.split(" ").take(5).joinToString(" ")
            memoryRepository.addNote(title = title, content = noteBody, category = "Voice Note", language = lang.code)
            val msg = when (lang) {
                LanguageMode.URDU -> "محفوظ کر لیا $user: \"$noteBody\"۔"
                LanguageMode.ROMAN_URDU -> "Save kar liya hai $user: \"$noteBody\"."
                else -> "Saved that note for you, $user: \"$noteBody\"."
            }
            return CommandOutcome.Handled(msg, lang)
        }
        if (containsAny(lower, listOf("show notes", "my notes", "read notes", "mere notes", "notes dikhao"))) {
            memoryRepository.recordCommandUsage("read_notes", cleaned)
            val notes = memoryRepository.getNotesSnapshot()
            val msg = if (notes.isEmpty()) {
                if (lang == LanguageMode.ROMAN_URDU) "$user, abhi koi note save nahi hai. Jab chahein ek note likhwa dein!"
                else "You don't have any saved notes yet, $user."
            } else {
                val preview = notes.take(4).joinToString("\n") { "• ${it.title}: ${it.content}" }
                "Here are your latest notes, $user:\n$preview"
            }
            return CommandOutcome.Handled(msg, lang)
        }

        // 14. Confirmation-Gated Calls & SMS
        val callTarget = extractCallTarget(cleaned)
        if (callTarget != null) {
            memoryRepository.recordCommandUsage("call_dialer", cleaned)
            val msg = when (lang) {
                LanguageMode.ROMAN_URDU -> "$user, kya main $callTarget par call dialer khol doon? Confirm kar dein."
                LanguageMode.URDU -> "$user، کیا میں $callTarget پر کال کرنے کے لیے ڈائلر کھولوں؟"
                else -> "Just to be sure, $user — should I open the dialer for $callTarget?"
            }
            return CommandOutcome.Handled(
                responseText = msg,
                language = lang,
                confirmation = PendingConfirmation.Call(callTarget, callTarget)
            )
        }
        val smsTarget = extractSmsTarget(cleaned)
        if (smsTarget != null) {
            memoryRepository.recordCommandUsage("sms_composer", cleaned)
            val msg = "Just confirming, $user — ready to open your SMS app for ${smsTarget.first}?"
            return CommandOutcome.Handled(
                responseText = msg,
                language = lang,
                confirmation = PendingConfirmation.Sms(smsTarget.first, smsTarget.second)
            )
        }

        // 15. Open Installed App / Camera / Settings
        val appTarget = extractAppToOpen(cleaned)
        if (appTarget != null) {
            memoryRepository.recordCommandUsage("open_app", cleaned)
            return formatDeviceResult(deviceController.launchAppByQuery(appTarget), lang, user)
        }

        // 16. Mathematical Calculations
        if (MathEvaluator.looksLikeMath(cleaned)) {
            memoryRepository.recordCommandUsage("calculator", cleaned)
            return when (val result = MathEvaluator.evaluate(cleaned)) {
                is MathResult.Success -> {
                    val msg = when (lang) {
                        LanguageMode.URDU -> "$user، اس کا جواب ${result.formatted} ہے۔"
                        LanguageMode.ROMAN_URDU -> "$user, iska jawab ${result.formatted} aata hai!"
                        else -> "That comes out to ${result.formatted}, $user."
                    }
                    CommandOutcome.Handled(msg, lang)
                }
                is MathResult.Error -> {
                    CommandOutcome.Handled("Hmm $user, I couldn't compute that: ${result.reason}", lang)
                }
            }
        }

        // 17. Fallback to Online AI (+ Live Web Search when helpful) or honest Offline reply
        return CommandOutcome.NeedsOnlineAi(cleaned, lang, mood)
    }

    private fun formatDeviceResult(res: DeviceActionResult, lang: LanguageMode, user: String): CommandOutcome.Handled {
        return when (res) {
            is DeviceActionResult.Success -> {
                val msg = when (lang) {
                    LanguageMode.ROMAN_URDU -> "Bilkul $user! ${res.message}"
                    LanguageMode.URDU -> "جی $user، ${res.message}"
                    else -> "On it, $user! ${res.message}"
                }
                CommandOutcome.Handled(msg, lang)
            }
            is DeviceActionResult.Error -> CommandOutcome.Handled("Sorry $user, ${res.message}", lang)
        }
    }

    private fun matchesCapabilities(lower: String): Boolean {
        return containsAny(lower, listOf("what can you do", "kya kar sakte ho", "capabilities", "commands list"))
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

    private fun extractBrowserSearchQuery(cleaned: String): String? {
        val patterns = listOf(
            Regex("""(?:google\s+(?:par|pe|on)\s+)(.+?)(?:\s+search\s+karo|\s+search)?$""", RegexOption.IGNORE_CASE),
            Regex("""(?:open\s+google\s+(?:for|and\s+search)\s+)(.+)$""", RegexOption.IGNORE_CASE)
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
        return cleaned
            .replace(Regex("""(?i)^(?:please\s+)?(?:ek\s+)?(?:save\s+a?\s*note|note\s+save\s+karo|ek\s+note\s+save\s+karo|take\s+a\s+note|add\s+note|note\s+likho)\s*(?:that|ke|ki|:|-)?\s*"""), "")
            .trim()
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
