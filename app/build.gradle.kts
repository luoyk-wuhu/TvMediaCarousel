plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.tvcarousel"
    compileSdk = 34
    defaultConfig { applicationId = "com.tvcarousel"; minSdk = 23; targetSdk = 34; versionCode = 1; versionName = "1.0.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
    lint { abortOnError = true }
}
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}
