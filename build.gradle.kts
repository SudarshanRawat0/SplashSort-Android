plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.yourcompany.watersort"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.yourcompany.watersort"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    // Unity Ads ONLY. No Google Mobile Ads / AdMob.
    // If Gradle says it cannot find 4.18.0, try 4.16.5 (also a documented release) and tell me.
    implementation("com.unity3d.ads:unity-ads:4.18.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
}
