plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "ch.lkmc.chessandcocoa"
    compileSdkVersion("android-37.0")

    defaultConfig {
        applicationId = "ch.lkmc.chessandcocoa"
        // Phones from the last two years (owner decision, concept Section 2.4)
        // run far newer Android; 29 keeps the floor modest without old-API code paths.
        minSdk = 29
        targetSdk = 37
        versionCode = 4
        versionName = "0.2.2"
    }

    signingConfigs {
        // A checked-in debug keystore signs BOTH build types: zero-secret CI,
        // reproducible builds, anyone can build an upgrade-compatible APK.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // ==== Stockfish native engine (in-process, libcocoafish) ==============
    // The whole JNI integration is kept inside this commented block so other
    // build-file work does not interleave. See app/src/main/cpp/.
    ndkVersion = libs.versions.ndk.get()

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    defaultConfig {
        externalNativeBuild {
            cmake {
                // Directory written by scripts/fetch-nnue.sh; the verified
                // network is embedded into the .so via upstream's INCBIN.
                arguments += "-DNNUE_DIR=${rootDir.absolutePath}/build/nnue"
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                // arm64-v8a for devices; x86_64 exists only for the emulator.
                abiFilters += setOf("arm64-v8a", "x86_64")
            }
        }
        release {
            ndk {
                abiFilters += setOf("arm64-v8a")
            }
        }
    }
    // ==== end Stockfish native engine =====================================
}

// ==== Stockfish native engine: NNUE fetch ================================
// The network is ~100 MB and gitignored; fetch-nnue.sh downloads it once and
// verifies that the file's sha256 prefix matches its name.
val nnueNetName: String by lazy {
    Regex("""EvalFileDefaultName "(nn-[0-9a-f]+\.nnue)"""")
        .find(rootDir.resolve("third_party/stockfish/src/evaluate.h").readText())
        ?.groupValues?.get(1)
        ?: error("EvalFileDefaultName not found in vendored Stockfish evaluate.h")
}

val fetchNnue = tasks.register<Exec>("fetchNnue") {
    group = "build"
    description = "Fetches and verifies the Stockfish NNUE network into build/nnue/."
    workingDir = rootDir
    commandLine("bash", "scripts/fetch-nnue.sh")
    inputs.file(rootDir.resolve("third_party/stockfish/src/evaluate.h"))
    inputs.property("nnueCacheDir", providers.environmentVariable("NNUE_CACHE_DIR").orElse(""))
    outputs.file(rootDir.resolve("build/nnue").resolve(nnueNetName))
}

// AGP creates one native task per variant and ABI; every one of them needs
// the verified network on disk before CMake configures.
tasks.configureEach {
    if (
        name.startsWith("externalNativeBuild") ||
        name.startsWith("configureCMake") ||
        name.startsWith("buildCMake")
    ) {
        dependsOn(fetchNnue)
    }
}
// ==== end Stockfish native engine ========================================

// ==== Legal assets =========================================================
// The GPL text and third-party notices ship inside the app (concept Section
// 14.4 release gate), copied from their single sources into generated assets.
abstract class CopyLegalAssets : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve("legal")
        target.deleteRecursively()
        target.mkdirs()
        sources.files.forEach { file ->
            val name = if (file.name == "LICENSE") "LICENSE.txt" else file.name
            file.copyTo(target.resolve(name), overwrite = true)
        }
    }
}

val copyLegalAssets = tasks.register<CopyLegalAssets>("copyLegalAssets") {
    sources.from(rootDir.resolve("LICENSE"), rootDir.resolve("legal/THIRD_PARTY_NOTICES.md"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyLegalAssets, CopyLegalAssets::outputDir)
    }
}
// ==== end Legal assets =====================================================

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(project(":coach"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Engine-backed UI tests use the host Stockfish (scripts/build-stockfish-host.sh)
// and skip themselves without it; screenshots land in docs/screenshots/.
tasks.withType<Test>().configureEach {
    systemProperty("stockfish.path", rootProject.file("build/stockfish-host/stockfish").absolutePath)
    systemProperty("screenshots.dir", rootProject.file("docs/screenshots").absolutePath)
}
