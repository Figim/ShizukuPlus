package af.shizuku.manager.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import timber.log.Timber

/**
 * Wi-Fi network names for the trusted-network pickers. Android only gives an app the current SSID
 * with location access, and never the saved list; the shell user can read both through
 * `cmd wifi`, so with the service running nothing has to be typed or granted.
 */
object WifiNetworks {
    data class Snapshot(
        /** The connected network, or null when not on Wi-Fi or it can't be read. */
        val current: String?,
        /** Networks saved on the device, sorted; empty when they can't be read. */
        val saved: List<String>,
        /** False when the service isn't running, so only [current] (via location) was tried. */
        val viaShell: Boolean,
    )

    suspend fun load(context: Context): Snapshot =
        withContext(Dispatchers.IO) {
            val shell = shellAvailable()
            val saved = if (shell) shell("cmd wifi list-networks")?.let(::parseSavedNetworks).orEmpty() else emptyList()
            Snapshot(current(context), saved, shell)
        }

    /** The connected network's name: through location access if granted, else through the shell. */
    suspend fun current(context: Context): String? =
        withContext(Dispatchers.IO) {
            NetworkTrust.currentSsid(context) ?: if (shellAvailable()) shell("cmd wifi status")?.let(::parseStatus) else null
        }

    private fun shellAvailable() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** `Wifi is connected to "Home"` (older builds print `SSID: "Home", BSSID: …` instead). */
    internal fun parseStatus(output: String): String? {
        val match =
            Regex("""connected to "(.*)"""").find(output)
                ?: Regex("""SSID: "(.*?)",""").find(output)
        return match
            ?.groupValues
            ?.get(1)
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    /**
     * Rows of `Network Id  SSID  Security type`: the id is the first word and the security type
     * the last, so whatever is between them is the name, spaces included.
     */
    internal fun parseSavedNetworks(output: String): List<String> =
        output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.first().isDigit() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 3) return@mapNotNull null
                line
                    .substringAfter(parts.first())
                    .substringBeforeLast(parts.last())
                    .trim()
                    .removeSurrounding("\"")
                    .takeIf { it.isNotEmpty() }
            }.distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()

    private suspend fun shell(command: String): String? =
        runCatching {
            @Suppress("DEPRECATION")
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            coroutineScope {
                val output = async { process.inputStream.bufferedReader().use { it.readText() } }
                // A timeout alone can't unblock the read; destroying the process closes the pipe.
                withTimeoutOrNull(SHELL_TIMEOUT_MS) { output.await() }.also { if (it == null) process.destroy() }
            }
        }.onFailure { Timber.w(it, "WifiNetworks: %s failed", command) }
            .getOrNull()

    private const val SHELL_TIMEOUT_MS = 5_000L
}
