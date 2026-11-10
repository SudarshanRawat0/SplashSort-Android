package com.yourcompany.watersort.ads

/** SINGLE place for all Unity Ads settings. No Google AdMob anywhere in this project. */
object AdsConfig {
    const val ADS_ENABLED = true             // ON for this test build (TEST_MODE below)
    const val TEST_MODE = true               // MUST be false for the release build

    // From your Unity dashboard. VERIFY each value there; the Game ID is NOT a placement ID.
    const val GAME_ID = "800428609"
    const val BANNER_ID = "BP_Banner_Android"
    const val INTERSTITIAL_ID = "BP_Interstitial_Android"
    const val REWARDED_ID = "BP_Rewarded_Android"

    const val INTERSTITIAL_EVERY_LEVELS = 3  // only after the NEXT LEVEL tap
    const val INTERSTITIAL_MIN_SECONDS = 90
    const val RETRY_LOAD_MS = 30_000L

    fun toJson() = """{"adsEnabled":$ADS_ENABLED,"interstitialEveryLevels":$INTERSTITIAL_EVERY_LEVELS,"interstitialMinSeconds":$INTERSTITIAL_MIN_SECONDS}"""
}
