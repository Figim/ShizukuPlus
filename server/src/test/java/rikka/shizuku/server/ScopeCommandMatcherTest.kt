package rikka.shizuku.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import rikka.shizuku.server.util.ScopeCommandMatcher
import rikka.shizuku.server.util.ScopeCommandMatcher.ACTIVITY_MANAGER
import rikka.shizuku.server.util.ScopeCommandMatcher.APPOPS
import rikka.shizuku.server.util.ScopeCommandMatcher.PACKAGE_MANAGER
import rikka.shizuku.server.util.ScopeCommandMatcher.SYSTEM_SETTINGS

class ScopeCommandMatcherTest {
    private fun scopes(vararg cmd: String) = ScopeCommandMatcher.requiredScopes(arrayOf(*cmd))

    @Test
    fun directExec() {
        assertEquals(setOf(PACKAGE_MANAGER), scopes("pm", "suspend", "com.example"))
        assertEquals(setOf(ACTIVITY_MANAGER), scopes("am", "force-stop", "com.example"))
        assertEquals(setOf(APPOPS), scopes("appops", "set", "com.example", "CAMERA", "deny"))
        assertEquals(setOf(SYSTEM_SETTINGS), scopes("settings", "put", "global", "x", "1"))
    }

    @Test
    fun shellWrappedForms_issue648() {
        // aShell and terminals: sh -c "<command>"
        assertEquals(setOf(PACKAGE_MANAGER), scopes("sh", "-c", "pm suspend com.example"))
        assertEquals(setOf(PACKAGE_MANAGER), scopes("sh", "-c", "/system/bin/pm suspend com.example"))
        assertEquals(setOf(PACKAGE_MANAGER), scopes("sh", "-c", "cd /sdcard;pm list packages"))
        assertEquals(setOf(PACKAGE_MANAGER), scopes("sh", "-c", "echo hi && pm disable-user x"))
        assertEquals(setOf(PACKAGE_MANAGER), scopes("sh", "-c", "\"pm\" list packages"))
        assertEquals(setOf(PACKAGE_MANAGER), scopes("sh", "-c", "cmd   package list packages"))
        assertEquals(setOf(ACTIVITY_MANAGER), scopes("sh", "-c", "cmd activity get-current-user"))
        assertEquals(setOf(SYSTEM_SETTINGS), scopes("sh", "-c", "svc wifi disable"))
        assertEquals(setOf(SYSTEM_SETTINGS), scopes("sh", "-c", "cmd power set-fixed-performance-mode-enabled true"))
    }

    @Test
    fun severalToolsInOneCommand() {
        assertEquals(
            setOf(PACKAGE_MANAGER, ACTIVITY_MANAGER),
            scopes("sh", "-c", "pm clear x; am start -n x/.Main"),
        )
    }

    @Test
    fun wordsThatOnlyContainToolNames() {
        assertEquals(emptySet<String>(), scopes("sh", "-c", "echo spam > /sdcard/npm.log"))
        assertEquals(emptySet<String>(), scopes("ls", "/data/local/tmp/pmtools"))
        assertEquals(emptySet<String>(), scopes("sh", "-c", "cat /sdcard/settings.json"))
        assertEquals(emptySet<String>(), scopes("sh", "-c", "date +%I:%M%p"))
        assertEquals(emptySet<String>(), scopes("logcat", "-d", "-s", "ExampleAm"))
        assertEquals(emptySet<String>(), ScopeCommandMatcher.requiredScopes(null))
        assertEquals(emptySet<String>(), ScopeCommandMatcher.requiredScopes(emptyArray()))
    }
}
