package rikka.shizuku.server.util

/**
 * Maps a newProcess command line to the per-app API scopes (#645) it exercises.
 *
 * Clients rarely exec `pm` directly: aShell and most terminals run `sh -c "<command>"`, often
 * with full paths (`/system/bin/pm`), chains (`cd x; pm list`) or quotes. Matching on a leading
 * "pm " or " pm " missed all of those (#648), so tools are matched as whole words anywhere in the
 * joined command, with shell separators and path slashes as word boundaries.
 *
 * Commands written to an interactive shell's stdin later can't be seen here; only the "shell"
 * scope can block those.
 */
object ScopeCommandMatcher {
    const val PACKAGE_MANAGER = "package_manager"
    const val ACTIVITY_MANAGER = "activity_manager"
    const val APPOPS = "appops"
    const val SYSTEM_SETTINGS = "system_settings"

    // Start of string, whitespace, a shell separator or quote, or the '/' of a path.
    private const val B = """(?:^|[\s;&|()`'"/])"""

    // Followed by end of string, whitespace, a separator or a quote.
    private const val E = """(?=$|[\s;&|()`'"])"""

    private val rules: List<Pair<String, Regex>> =
        listOf(
            PACKAGE_MANAGER to Regex("${B}(?:pm${E}|cmd\\s+package${E})"),
            ACTIVITY_MANAGER to Regex("${B}(?:am${E}|cmd\\s+(?:activity|activity_task)${E})"),
            APPOPS to Regex("${B}(?:appops${E}|cmd\\s+appops${E})"),
            SYSTEM_SETTINGS to Regex("${B}(?:settings${E}|svc${E}|cmd\\s+(?:settings|power)${E})"),
        )

    @JvmStatic
    fun requiredScopes(cmd: Array<String>?): Set<String> {
        if (cmd.isNullOrEmpty()) return emptySet()
        val full = cmd.joinToString(" ")
        return rules.filter { (_, re) -> re.containsMatchIn(full) }.mapTo(LinkedHashSet()) { it.first }
    }
}
