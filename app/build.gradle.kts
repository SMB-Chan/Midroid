plugins {
    id("com.android.application")
}

val updateSigningStoreFile = System.getenv("MIDROID_SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() }
val updateSigningStorePassword = System.getenv("MIDROID_SIGNING_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
val updateSigningKeyAlias = System.getenv("MIDROID_SIGNING_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val updateSigningKeyPassword = System.getenv("MIDROID_SIGNING_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val hasUpdateSigning = listOf(
    updateSigningStoreFile,
    updateSigningStorePassword,
    updateSigningKeyAlias,
    updateSigningKeyPassword,
).all { it != null }

val launcherIconSource = layout.projectDirectory.file("launcher-icon-foreground.b64")
val generatedLauncherResDir = layout.buildDirectory.dir("generated/midroidLauncher/res")
val generateLauncherIcon = tasks.register("generateLauncherIcon") {
    inputs.file(launcherIconSource)
    outputs.dir(generatedLauncherResDir)

    doLast {
        val encoded = launcherIconSource.asFile.readText().trim()
        check(encoded.isNotBlank()) { "launcher-icon-foreground.b64 is empty" }

        val targetDir = generatedLauncherResDir.get().asFile
            .resolve("mipmap-xxxhdpi")
            .apply { mkdirs() }
        targetDir.resolve("ic_launcher_foreground.webp").writeBytes(
            java.util.Base64.getDecoder().decode(encoded),
        )
    }
}

android {
    namespace = "dev.midroid.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.midroid.app"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("MIDROID_VERSION_CODE")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        versionName = System.getenv("MIDROID_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "0.1.0"
    }

    sourceSets {
        getByName("main").res.srcDir(generatedLauncherResDir)
    }

    signingConfigs {
        if (hasUpdateSigning) {
            create("update") {
                storeFile = file(updateSigningStoreFile!!)
                storePassword = updateSigningStorePassword
                keyAlias = updateSigningKeyAlias
                keyPassword = updateSigningKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // CI/debug builds deliberately use the normal ephemeral debug identity.
            // Never sign a debuggable APK with the long-lived update key.
            isDebuggable = true
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasUpdateSigning) {
                signingConfig = signingConfigs.getByName("update")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateLauncherIcon)
}

dependencies {
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.webkit:webkit:1.17.0")
    testImplementation("junit:junit:4.13.2")
}
