package com.osaf.jarvis.utils

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

sealed class MathResult {
    data class Success(val formatted: String, val value: Double, val expression: String) : MathResult()
    data class Error(val reason: String) : MathResult()
}

object MathEvaluator {

    private val fillerWords = setOf(
        "calculate", "compute", "what", "is", "whats", "what's", "equals", "equal",
        "hisab", "batao", "kitna", "kitni", "hota", "hoti", "hai", "ka", "ki", "ko",
        "jarvis", "hey", "please", "zara", "solve", "evaluate", "answer", "of"
    )

    fun looksLikeMath(input: String): Boolean {
        val normalized = normalizeNaturalLanguage(input)
        val hasDigit = normalized.any { it.isDigit() }
        val hasOp = normalized.any { it in charArrayOf('+', '-', '*', '/', '%', '^') }
        return hasDigit && hasOp
    }

    fun evaluate(rawInput: String): MathResult {
        val expr = normalizeNaturalLanguage(rawInput)
        if (expr.isBlank() || expr.none { it.isDigit() }) {
            return MathResult.Error("No valid mathematical expression found.")
        }
        return try {
            val parser = Parser(expr)
            val result = parser.parseExpression()
            if (!parser.isAtEnd()) {
                return MathResult.Error("Unexpected token near '${parser.peekChar()}'")
            }
            if (result.isNaN() || result.isInfinite()) {
                return MathResult.Error("Division by zero or overflow.")
            }
            MathResult.Success(
                formatted = formatNumber(result),
                value = result,
                expression = expr
            )
        } catch (e: ArithmeticException) {
            MathResult.Error(e.message ?: "Math error")
        } catch (e: IllegalArgumentException) {
            MathResult.Error(e.message ?: "Invalid expression")
        }
    }

    internal fun normalizeNaturalLanguage(input: String): String {
        var s = input.lowercase().trim()
        // Handle "X percent of Y" or "X% of Y" or "Y ka X percent/feesad"
        val percentOfRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:%|percent|percentage|feesad)\s*(?:of|ka|ki)?\s*(\d+(?:\.\d+)?)""")
        s = percentOfRegex.replace(s) { m ->
            "(${m.groupValues[1]}/100*${m.groupValues[2]})"
        }

        val urduPercentRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:ka|ki)\s*(\d+(?:\.\d+)?)\s*(?:%|percent|feesad)""")
        s = urduPercentRegex.replace(s) { m ->
            "(${m.groupValues[2]}/100*${m.groupValues[1]})"
        }

        // Replace natural-language operators
        s = s.replace("multiplied by", "*")
            .replace("multiply by", "*")
            .replace("multiply", "*")
            .replace("times", "*")
            .replace("zarab", "*")
            .replace("into", "*")
            .replace("×", "*")
            .replace("x", "*") // careful: handled below if inside word? Check word boundary!
        // Restore accidental 'x' inside words if any by doing word-boundary replacements properly:
        s = input.lowercase().trim()
        s = percentOfRegex.replace(s) { m -> "(${m.groupValues[1]}/100*${m.groupValues[2]})" }
        s = urduPercentRegex.replace(s) { m -> "(${m.groupValues[2]}/100*${m.groupValues[1]})" }

        val replacements = listOf(
            Regex("""\bmultiplied\s+by\b""") to "*",
            Regex("""\bmultiply\s+by\b""") to "*",
            Regex("""\bmultiply\b""") to "*",
            Regex("""\btimes\b""") to "*",
            Regex("""\bzarab\b""") to "*",
            Regex("""\bdivided\s+by\b""") to "/",
            Regex("""\bdivide\s+by\b""") to "/",
            Regex("""\bdivide\b""") to "/",
            Regex("""\btaqseem\b""") to "/",
            Regex("""\bplus\b""") to "+",
            Regex("""\bjama\b""") to "+",
            Regex("""\badd\b""") to "+",
            Regex("""\bminus\b""") to "-",
            Regex("""\bnafi\b""") to "-",
            Regex("""\bsubtract\b""") to "-",
            Regex("""\bpercent\b""") to "/100",
            Regex("""\bfeesad\b""") to "/100",
            Regex("""(?<=\d)\s*x\s*(?=\d)""") to "*"
        )
        for ((regex, rep) in replacements) {
            s = regex.replace(s, rep)
        }
        s = s.replace("×", "*").replace("÷", "/").replace("−", "-")

        // Strip filler words
        val wordRegex = Regex("""[a-z]+""")
        s = wordRegex.replace(s) { m ->
            if (m.value in fillerWords) " " else m.value
        }

        // Convert trailing standalone % like "20%" -> "(20/100)"
        s = Regex("""(\d+(?:\.\d+)?)\s*%""").replace(s) { m ->
            "(${m.groupValues[1]}/100)"
        }

        return s.filter { it.isDigit() || it in charArrayOf('+', '-', '*', '/', '.', '(', ')') }
    }

    private fun formatNumber(value: Double): String {
        val bd = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (abs(value) >= 1e12 || (value != 0.0 && abs(value) < 1e-6)) {
            bd.toString()
        } else {
            bd.toPlainString()
        }
    }

    private class Parser(private val str: String) {
        private var pos = 0

        fun isAtEnd(): Boolean = pos >= str.length
        fun peekChar(): Char = if (pos < str.length) str[pos] else '\u0000'

        fun parseExpression(): Double {
            var x = parseTerm()
            while (pos < str.length) {
                when (str[pos]) {
                    '+' -> { pos++; x += parseTerm() }
                    '-' -> { pos++; x -= parseTerm() }
                    else -> return x
                }
            }
            return x
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (pos < str.length) {
                when (str[pos]) {
                    '*' -> { pos++; x *= parseFactor() }
                    '/' -> {
                        pos++
                        val denom = parseFactor()
                        if (denom == 0.0) throw ArithmeticException("Cannot divide by zero.")
                        x /= denom
                    }
                    else -> return x
                }
            }
            return x
        }

        private fun parseFactor(): Double {
            if (pos < str.length && str[pos] == '+') {
                pos++
                return parseFactor()
            }
            if (pos < str.length && str[pos] == '-') {
                pos++
                return -parseFactor()
            }
            if (pos < str.length && str[pos] == '(') {
                pos++
                val x = parseExpression()
                if (pos >= str.length || str[pos] != ')') {
                    throw IllegalArgumentException("Missing closing parenthesis")
                }
                pos++
                return x
            }
            val start = pos
            while (pos < str.length && (str[pos].isDigit() || str[pos] == '.')) {
                pos++
            }
            if (start == pos) {
                throw IllegalArgumentException("Expected number at position $pos")
            }
            return str.substring(start, pos).toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid number format")
        }
    }
}
