package com.yourcompany.watersort.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import android.widget.FrameLayout
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsLoadOptions
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

/** Unity Ads ONLY. Every method is a no-op when ADS_ENABLED = false. */
class AdManager(
    private val act: Activity,
    private val bannerHost: FrameLayout,
    private val sendToJs: (String) -> Unit
) {
    private val tag = "AdManager"
    private val ui = Handler(Looper.getMainLooper())
    val consent = ConsentManager(act)
    @Volatile private var state = 0                    // 0 idle, 1 initialising, 2 ready (prevents double init)
    @Volatile private var interReady = false; private var interLoading = false
    @Volatile private var rewReady = false; private var rewLoading = false
    private var banner: BannerView? = null
    private var wantBanner = false

    private val logBuf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    /** Every event goes to Logcat (tag AdManager) AND to the in-game Settings > Ad status screen. */
    fun log(m: String) {
        Log.w(tag, m)
        synchronized(logBuf) { logBuf.addLast(fmt.format(Date()) + " " + m); while (logBuf.size > 40) logBuf.removeFirst() }
    }
    fun debug(): String = "ADS_ENABLED=${AdsConfig.ADS_ENABLED} TEST_MODE=${AdsConfig.TEST_MODE}\n" +
        "gameId=${AdsConfig.GAME_ID}\nplacements: ${AdsConfig.BANNER_ID} | ${AdsConfig.INTERSTITIAL_ID} | ${AdsConfig.REWARDED_ID}\n" +
        "state=$state (0 idle,1 init,2 ready) interstitialReady=$interReady rewardedReady=$rewReady bannerCreated=${banner != null}\n---\n" +
        synchronized(logBuf) { logBuf.joinToString("\n") }

    fun start() {
        if (!AdsConfig.ADS_ENABLED || state != 0) return
        state = 1
        log("start: waiting for consent")
        consent.ensure { log("consent done; UnityAds.initialize(gameId=${AdsConfig.GAME_ID}, test=${AdsConfig.TEST_MODE})"); initUnity() }
    }

    private fun initUnity() {
        consent.forwardToUnity()                       // consent first, then init
        if (AdsConfig.TEST_MODE) UnityAds.setDebugMode(true)   // verbose Unity SDK logs in Logcat (tag UnityAds)
        UnityAds.initialize(act.applicationContext, AdsConfig.GAME_ID, AdsConfig.TEST_MODE,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() { ui.post {
                    log("INIT OK"); state = 2; loadInter(); loadReward(); if (wantBanner) showBanner() }
                }
                override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                    log("INIT FAILED: $error | $message")
                    ui.post { state = 0; ui.postDelayed({ start() }, AdsConfig.RETRY_LOAD_MS) }
                }
            })
    }

    fun isReady(type: String) = AdsConfig.ADS_ENABLED && state == 2 && when (type) {
        "interstitial" -> interReady
        "rewarded" -> rewReady
        else -> false
    }

    fun showBanner() = ui.post {
        if (!AdsConfig.ADS_ENABLED) return@post
        start()
        if (state != 2) { wantBanner = true; return@post }
        if (banner == null) {
            log("creating banner ${AdsConfig.BANNER_ID}")
            banner = BannerView(act, AdsConfig.BANNER_ID, UnityBannerSize(320, 50)).also { b ->
                b.setListener(object : BannerView.IListener {
                    override fun onBannerLoaded(v: BannerView?) { log("banner LOADED") }
                    override fun onBannerShown(v: BannerView?) { log("banner SHOWN") }
                    override fun onBannerClick(v: BannerView?) {}
                    override fun onBannerLeftApplication(v: BannerView?) {}
                    override fun onBannerFailedToLoad(v: BannerView?, e: BannerErrorInfo?) {
                        log("BANNER FAILED: ${e?.errorMessage}")
                        ui.postDelayed({ v?.load() }, AdsConfig.RETRY_LOAD_MS)
                    }
                })
                bannerHost.addView(b, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))
                b.load()
            }
        }
        banner?.visibility = View.VISIBLE
    }

    fun hideBanner() = ui.post { wantBanner = false; banner?.visibility = View.GONE }

    fun show(type: String, id: Int) = ui.post {
        val rewarded = type == "rewarded"
        val placement = if (rewarded) AdsConfig.REWARDED_ID else AdsConfig.INTERSTITIAL_ID
        if (!isReady(type)) { emit(id, "failed"); return@post }
        if (rewarded) rewReady = false else interReady = false
        UnityAds.show(act, placement, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(p: String?, e: UnityAds.UnityAdsShowError?, m: String?) {
                log("SHOW FAILED: $e | $m"); ui.post { reload(rewarded); emit(id, "failed") }
            }
            override fun onUnityAdsShowStart(p: String?) { log("show START $p") }
            override fun onUnityAdsShowClick(p: String?) {}
            override fun onUnityAdsShowComplete(p: String?, s: UnityAds.UnityAdsShowCompletionState?) {
                // Reward ONLY when the ad was fully watched. SKIPPED or closed early = no reward.
                log("show COMPLETE $p state=$s")
                val ok = rewarded && s == UnityAds.UnityAdsShowCompletionState.COMPLETED
                ui.post { reload(rewarded); emit(id, if (ok) "rewarded" else "closed") }
            }
        })
    }

    private fun reload(rewarded: Boolean) = if (rewarded) loadReward() else loadInter()

    private fun loadInter() {
        if (state != 2 || interLoading || interReady) return
        log("loading interstitial ${AdsConfig.INTERSTITIAL_ID}")   // never load the same placement twice
        interLoading = true
        UnityAds.load(AdsConfig.INTERSTITIAL_ID, UnityAdsLoadOptions(), object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(p: String?) { log("interstitial LOADED"); ui.post { interLoading = false; interReady = true } }
            override fun onUnityAdsFailedToLoad(p: String?, e: UnityAds.UnityAdsLoadError?, m: String?) {
                log("INTERSTITIAL LOAD FAILED: $e | $m")
                ui.post { interLoading = false; ui.postDelayed({ loadInter() }, AdsConfig.RETRY_LOAD_MS) }
            }
        })
    }

    private fun loadReward() {
        if (state != 2 || rewLoading || rewReady) return
        log("loading rewarded ${AdsConfig.REWARDED_ID}")
        rewLoading = true
        UnityAds.load(AdsConfig.REWARDED_ID, UnityAdsLoadOptions(), object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(p: String?) { log("rewarded LOADED"); ui.post { rewLoading = false; rewReady = true } }
            override fun onUnityAdsFailedToLoad(p: String?, e: UnityAds.UnityAdsLoadError?, m: String?) {
                log("REWARDED LOAD FAILED: $e | $m")
                ui.post { rewLoading = false; ui.postDelayed({ loadReward() }, AdsConfig.RETRY_LOAD_MS) }
            }
        })
    }

    private fun emit(id: Int, r: String) = sendToJs("window.onAdEvent($id,'$r')")
    fun pause() {}
    fun resume() {}
    fun destroy() { banner?.destroy(); banner = null }
}
