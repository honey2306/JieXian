package com.jiexian.app.domain

/**
 * Parses `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
 *
 * The value is a `:`-separated list of `package/class` entries, but vendors rewrite it in
 * different shapes: the class may be fully qualified, start with a `.` or omit the package
 * prefix entirely. A strict string comparison against `ComponentName.flattenToString()`
 * therefore reports a disabled service on those devices, and the app then asks the user to
 * re-authorize something that is still enabled. Compare normalized package and class names
 * instead.
 */
object AccessibilitySettings {
    fun isServiceListed(
        value: String?,
        packageName: String,
        serviceClass: String,
    ): Boolean {
        val expected = normalize("$packageName/$serviceClass", packageName) ?: return false
        return value
            .orEmpty()
            .split(':')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { normalize(it, packageName) }
            .any { it == expected }
    }

    private fun normalize(
        entry: String,
        fallbackPackage: String,
    ): String? {
        val parts = entry.split('/')
        if (parts.size != 2) return null
        val packagePart = parts[0].trim().ifEmpty { fallbackPackage }
        val classPart = parts[1].trim()
        if (packagePart.isEmpty() || classPart.isEmpty()) return null
        val classPartResolved =
            when {
                classPart.startsWith(".") -> packagePart + classPart
                !classPart.contains('.') -> "$packagePart.$classPart"
                else -> classPart
            }
        return "$packagePart/$classPartResolved".lowercase()
    }
}
