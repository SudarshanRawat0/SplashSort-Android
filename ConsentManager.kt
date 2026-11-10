package com.yourcompany.watersort.ads

import android.app.Activity
import android.app.AlertDialog
import com.unity3d.ads.metadata.MetaData

/**
 * Simple in-app consent prompt (Unity has no built-in dialog). Choice is stored and sent to Unity
 * via MetaData BEFORE initialisation. NOT a certified CMP: have your legal advisor review it.
 */
class ConsentManager(private val act: Activity) {
    private val prefs = act.getSharedPreferences("ads_consent", Activity.MODE_PRIVATE)
    val privacyRequired = true                      // always offer the Settings > Privacy button
    private fun saved(): Int = prefs.getInt("choice", -1)   // -1 unknown, 0 declined, 1 accepted

    fun ensure(done: () -> Unit) {
        if (saved() != -1) { done(); return }
        ask(done)
    }

    private fun ask(done: () -> Unit) = act.runOnUiThread {
        AlertDialog.Builder(act)
            .setTitle("Ads & privacy")
            .setMessage("Splash Sort is free thanks to ads from Unity Ads. Allow personalised ads? If you decline you still get ads, but not personalised. You can change this in Settings.")
            .setCancelable(false)
            .setPositiveButton("Allow") { _, _ -> save(1); done() }
            .setNegativeButton("Don't allow") { _, _ -> save(0); done() }
            .show()
    }

    private fun save(v: Int) { prefs.edit().putInt("choice", v).apply(); forwardToUnity() }

    /** Forward the stored choice to Unity (GDPR + US state privacy keys). */
    fun forwardToUnity() {
        val granted = saved() == 1
        MetaData(act.applicationContext).apply {
            set("gdpr.consent", granted); commit()
        }
        MetaData(act.applicationContext).apply {
            set("privacy.consent", granted); commit()
        }
    }

    fun showPrivacyOptions() = ask { }              // changing it re-sends consent; fully applied next launch
}
