plugins {
    alias(libs.plugins.kotlin.jvm)
    application
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

application {
    mainClass.set("ch.lkmc.chessandcocoa.harness.MainKt")
}

dependencies {
    implementation(project(":coach"))
    implementation(libs.kotlinx.coroutines.core)
}
