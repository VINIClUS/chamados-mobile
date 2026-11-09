plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    // Aplicado na raiz para checar também build.gradle.kts e settings.gradle.kts.
    alias(libs.plugins.ktlint)
}

ktlint {
    version = libs.versions.ktlint
}
