// Every plugin used by a module is declared here once, so all modules share one build classpath.
// The Kotlin JVM plugin also pins the Kotlin version used by AGP's built-in Kotlin support.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
