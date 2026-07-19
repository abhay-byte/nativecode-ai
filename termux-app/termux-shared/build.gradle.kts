plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("androidx.core:core:1.12.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("io.noties.markwon:recycler:4.6.2")
    implementation("commons-io:commons-io:2.15.1")
    implementation("androidx.window:window:1.0.0-alpha09")
    implementation(project(":modules:termux-app:terminal-view"))
}
