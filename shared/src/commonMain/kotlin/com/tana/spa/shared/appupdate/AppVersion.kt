package com.tana.spa.shared.appupdate

/**
 * A release version as the stores show it. Missing segments are zero, so `1.0` and `1.0.0` are the
 * same version, and segments compare numerically — `1.10.0` is newer than `1.9.0`, which string
 * comparison gets wrong.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int = compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private const val MAX_SEGMENTS = 3

        /** Null unless [raw] is one to three dot-separated non-negative integers. */
        fun parseOrNull(raw: String): AppVersion? {
            val segments = raw.trim().split('.')
            if (segments.size > MAX_SEGMENTS) return null
            val numbers = segments.map { segment -> segment.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
            return AppVersion(numbers[0], numbers.getOrElse(1) { 0 }, numbers.getOrElse(2) { 0 })
        }
    }
}
