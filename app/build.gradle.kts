plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin); no kotlin-android plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// SPEC §19.1. Supabase values are optional and default to "" (friend battles off).
val supabaseUrl = providers.gradleProperty("SUPABASE_URL").getOrElse("")
val supabaseAnonKey = providers.gradleProperty("SUPABASE_ANON_KEY").getOrElse("")
val rulesUrl =
    "https://raw.githubusercontent.com/nitinreddy08/stopdoom/main/rules/detection-rules.json"

android {
    namespace = "app.unrot"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.unrot"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "RULES_URL", "\"$rulesUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        // SPEC §19.1: avoid toolchain churn breaking builds.
        allWarningsAsErrors = false
    }
}

// SPEC §19.1: ship the repo's rules file inside the APK. The copied file is git-ignored.
val copyRules = tasks.register<Copy>("copyRules") {
    description = "Copies rules/detection-rules.json into the app's assets."
    from(rootProject.layout.projectDirectory.file("rules/detection-rules.json"))
    into(layout.projectDirectory.dir("src/main/assets"))
}

tasks.named("preBuild") {
    dependsOn(copyRules)
}

// JVM unit tests (src/test) run on JUnit 5, like :detection.
val rulesFileForTests = rootProject.file("rules/detection-rules.json")
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    inputs.file(rulesFileForTests)
    systemProperty("unrot.rulesFile", rulesFileForTests.absolutePath)
    testLogging {
        events("passed", "skipped", "failed")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
