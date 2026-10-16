plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Bytecode level 17, matching :app; any JDK 17+ builds it.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":chess"))
    api(project(":engine"))
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

// Engine-backed tests run against a host Stockfish built by
// scripts/build-stockfish-host.sh; they skip themselves when it is absent.
tasks.test {
    systemProperty("stockfish.path", rootProject.file("build/stockfish-host/stockfish").absolutePath)
}
