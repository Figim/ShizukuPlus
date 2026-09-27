package af.shizuku.manager.scripting

import af.shizuku.core.ui.AppBarFragmentActivity
import androidx.fragment.app.Fragment

class ScriptingActivity : AppBarFragmentActivity() {
    override fun createFragment(): Fragment = ScriptingFragment()
}
