plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.kukifyjeff.safepatrol"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kukifyjeff.safepatrol"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.5.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "major"

    productFlavors {

        create("QH1") {
            dimension = "major"
            applicationId = "com.kukifyjeff.safepatrol.qh1"
            versionNameSuffix = "-QH1"
        }
        create("QH2") {
            dimension = "major"
            applicationId = "com.kukifyjeff.safepatrol.qh2"
            versionNameSuffix = "-QH2"
        }
        create("DQ") {
            dimension = "major"
            applicationId = "com.kukifyjeff.safepatrol.dq"
            versionNameSuffix = "-DQ"
        }
        create("RJ") {
            dimension = "major"
            applicationId = "com.kukifyjeff.safepatrol.rj"
            versionNameSuffix = "-RJ"
        }
        create("KF") {
            dimension = "major"
            applicationId = "com.kukifyjeff.safepatrol.kf"
            versionNameSuffix = "-KF"
        }
        create("JH") {
            dimension = "major"
            applicationId = "com.kukifyjeff.safepatrol.jh"
            versionNameSuffix = "-JH"
        }
        create("LGI") {
            dimension = "major"
            applicationId = "com.kukifyjeff.safepatrol.lgi"
            versionNameSuffix = "-LGI"
        }
        create("LGO") {
            dimension = "major"
            applicationId = "com.kukifyjeff.safepatrol.lgo"
            versionNameSuffix = "-LGO"
        }
    }
    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // 你之后要用到的库（先放上，避免来回改）
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // CSV
    implementation("com.opencsv:opencsv:5.9")

    // AndroidX SplashScreen API
    implementation("androidx.core:core-splashscreen:1.0.1")


    // Zip 加密
//    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
//    implementation("org.apache.poi:ooxml-schemas:1.4")
    implementation("org.apache.xmlbeans:xmlbeans:5.1.1")
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    implementation("androidx.recyclerview:recyclerview:1.3.2")
}