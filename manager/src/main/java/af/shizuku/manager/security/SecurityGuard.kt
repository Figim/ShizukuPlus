package af.shizuku.manager.security

import java.io.File

object SecurityGuard {
    /**
     * Checks for common hooking frameworks like Xposed or Frida.
     */
    fun isTampered(): Boolean = checkXposed() || checkFrida()

    private fun checkXposed(): Boolean {
        try {
            val classLoader = ClassLoader.getSystemClassLoader()
            classLoader.loadClass("de.robv.android.xposed.XposedBridge")
            return true
        } catch (_: Exception) {
            // Not Xposed
        }
        return false
    }

    private fun checkFrida(): Boolean {
        // Frida often leaves artifacts in memory or specific ports
        val fridaFiles =
            listOf(
                "/data/local/tmp/frida-server",
                "/data/local/tmp/re.frida.server",
            )
        for (path in fridaFiles) {
            if (File(path).exists()) return true
        }
        return false
    }
}
