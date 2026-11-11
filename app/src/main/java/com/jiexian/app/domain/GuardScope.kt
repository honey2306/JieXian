package com.jiexian.app.domain

/**
 * Decides which foreground packages a [com.jiexian.app.model.RuleType.ALLOW_ONLY] rule may block.
 *
 * Only apps the user can actually launch are eligible, and a small set of system packages
 * (home screen, system UI, input methods, permission dialogs) is never restricted so the
 * device stays operable.
 */
data class GuardScope(
    val launchablePackages: Set<String>,
    val protectedPackages: Set<String>,
) {
    fun canRestrict(packageName: String): Boolean =
        packageName.isNotBlank() &&
            packageName in launchablePackages &&
            packageName !in protectedPackages
}
