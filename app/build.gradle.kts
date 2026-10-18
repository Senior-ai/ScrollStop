import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Release signing key, from `keystore.properties` (local, never committed) or environment
 * variables (CI). Without one, release builds fall back to the debug key: fine for your own
 * phone, but never publish those.
 */
val releaseKey: Map<String, String>? = run {
    val file = rootProject.file("keystore.properties")
    val props = Properties().apply { if (file.exists()) file.inputStream().use(::load) }
    fun value(prop: String, env: String) = props.getProperty(prop) ?: System.getenv(env)
    val storeFile = value("storeFile", "SCROLLSTOP_KEYSTORE_FILE") ?: return@run null
    mapOf(
        "storeFile" to storeFile,
        "storePassword" to value("storePassword", "SCROLLSTOP_KEYSTORE_PASSWORD").orEmpty(),
        "keyAlias" to value("keyAlias", "SCROLLSTOP_KEY_ALIAS").orEmpty(),
        "keyPassword" to value("keyPassword", "SCROLLSTOP_KEY_PASSWORD").orEmpty(),
    )
}

android {
    namespace = "com.senior.scrollstop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.senior.scrollstop"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = rootProject.file(releaseKey.getValue("storeFile"))
                storePassword = releaseKey.getValue("storePassword")
                keyAlias = releaseKey.getValue("keyAlias")
                keyPassword = releaseKey.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    testImplementation("junit:junit:4.13.2")
}

/**
 * Safety net: fails the build if the release manifest gains any permission or any exported
 * component beyond what ScrollStop needs. Runs as part of `./gradlew check`.
 */
val checkPermissions by tasks.registering {
    val manifest = layout.buildDirectory.file(
        "intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml",
    )
    dependsOn("processReleaseMainManifest")
    inputs.file(manifest)
    doLast {
        val allowedPermissions = setOf("com.senior.scrollstop.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val allowedExported = setOf("com.senior.scrollstop.ui.MainActivity")
        val ns = "http://schemas.android.com/apk/res/android"
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest.get().asFile)

        fun names(tag: String, filter: (org.w3c.dom.Element) -> Boolean = { true }): List<String> {
            val nodes = doc.getElementsByTagName(tag)
            return (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
                .filter(filter).map { it.getAttributeNS(ns, "name") }
        }

        val permissions = names("uses-permission") + names("uses-permission-sdk-23")
        val exported = listOf("activity", "activity-alias", "service", "receiver", "provider")
            .flatMap { tag -> names(tag) { it.getAttributeNS(ns, "exported") == "true" } }

        val problems = permissions.filterNot { it in allowedPermissions }.map { "permission $it" } +
            exported.filterNot { it in allowedExported }.map { "exported component $it" }
        check(problems.isEmpty()) {
            "Release manifest has unexpected entries (review before allowing):\n  " + problems.joinToString("\n  ")
        }
        logger.lifecycle("checkPermissions: OK (permissions=$permissions, exported=$exported)")
    }
}
tasks.named("check") { dependsOn(checkPermissions) }
