package com.yourcompany.watersort

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.yourcompany.watersort.ads.AdManager
import com.yourcompany.watersort.ads.AdsConfig

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var ads: AdManager

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        web = WebView(this).apply {
            setBackgroundColor(0xFF090A12.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true            // localStorage = saved progress
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
        }
        val bannerHost = FrameLayout(this)               // sits over the reserved #adslot
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(bannerHost, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)

        ads = AdManager(this, bannerHost) { js -> runOnUiThread { web.evaluateJavascript(js, null) } }
        web.addJavascriptInterface(Bridge(), "AndroidAds")
        web.loadUrl("file:///android_asset/www/index.html")
        ads.start()
    }

    inner class Bridge {
        @JavascriptInterface fun config() = AdsConfig.toJson()
        @JavascriptInterface fun isReady(type: String) = ads.isReady(type)
        @JavascriptInterface fun show(type: String, id: Int) = ads.show(type, id)
        @JavascriptInterface fun showBanner() = ads.showBanner()
        @JavascriptInterface fun hideBanner() = ads.hideBanner()
        @JavascriptInterface fun debug(): String = ads.debug()
        @JavascriptInterface fun privacyRequired() = AdsConfig.ADS_ENABLED && ads.consent.privacyRequired
        @JavascriptInterface fun showPrivacy() { runOnUiThread { ads.consent.showPrivacyOptions() } }
    }

    override fun onResume() { super.onResume(); web.onResume(); ads.resume() }
    override fun onPause() { ads.pause(); web.onPause(); super.onPause() }
    override fun onDestroy() { ads.destroy(); web.destroy(); super.onDestroy() }
}
