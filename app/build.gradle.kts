plugins { id("com.android.application") }
android {
 namespace = "dev.ankigate"
 compileSdk = 36
 defaultConfig { applicationId = "dev.ankigate"; minSdk = 30; targetSdk = 36; versionCode = 6; versionName = "0.6-test" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
