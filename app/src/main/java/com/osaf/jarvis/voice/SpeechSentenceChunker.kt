package com.osaf.jarvis.voice

/**
 * Splits streaming LLM deltas into natural speakable sentences so TTS playback
 * begins on the very first sentence without waiting for the full completion.
 */
class SpeechSentenceChunker(private val minChunkChars: Int = 18) {

    private val buffer = StringBuilder()

    fun appendAndExtract(delta: String): List<String> {
        if (delta.isEmpty()) return emptyList()
        buffer.append(delta)
        val ready = mutableListOf<String>()

        while (true) {
            val current = buffer.toString()
            val boundaryIdx = findSentenceBoundary(current)
            if (boundaryIdx < 0) break

            val rawSentence = current.substring(0, boundaryIdx + 1)
            val cleaned = cleanForSpeech(rawSentence)
            buffer.delete(0, boundaryIdx + 1)
            if (cleaned.isNotEmpty()) {
                ready.add(cleaned)
            }
        }
        return ready
    }

    fun flushRemaining(): String? {
        val cleaned = cleanForSpeech(buffer.toString())
        buffer.clear()
        return cleaned.ifEmpty { null }
    }

    fun reset() {
        buffer.clear()
    }

    private fun findSentenceBoundary(text: String): Int {
        for (i in text.indices) {
            val ch = text[i]
            val isTerminator = ch == '.' || ch == '!' || ch == '?' || ch == '۔' || ch == '\n'
            if (!isTerminator) continue
            if (i + 1 < minChunkChars) continue

            // Avoid splitting decimal numbers like 3.14 or abbreviations like e.g.
            if (ch == '.' && i > 0 && i + 1 < text.length && text[i - 1].isDigit() && text[i + 1].isDigit()) {
                continue
            }
            return i
        }
        return -1
    }

    companion object {
        fun cleanForSpeech(raw: String): String {
            return raw
                .replace(Regex("```[\\s\\S]*?```"), " code snippet ")
                .replace(Regex("https?://\\S+"), "link")
                .replace(Regex("[*_`#•~|]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
