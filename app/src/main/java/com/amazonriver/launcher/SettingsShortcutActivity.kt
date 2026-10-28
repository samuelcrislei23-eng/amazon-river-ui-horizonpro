package com.amazonriver.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** App-atalho: só joga para as configurações do launcher. */
class SettingsShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }
}
