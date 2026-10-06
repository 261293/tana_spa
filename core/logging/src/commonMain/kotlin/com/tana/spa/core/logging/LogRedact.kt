package com.tana.spa.core.logging

/** Masks personal data so logs stay useful for tracing without becoming a privacy liability. */
object LogRedact {
    /** `jane.doe@example.com` -> `j***@example.com` */
    fun email(value: String): String {
        val at = value.indexOf('@')
        if (at <= 0) return "***"
        return "${value.first()}***${value.substring(at)}"
    }

    /** `+84901234567` -> `***67` */
    fun phone(value: String): String {
        val digits = value.filter { it.isDigit() }
        return if (digits.length < 2) "***" else "***${digits.takeLast(2)}"
    }
}
