import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.openapi.generator)
}

val openApiContract =
    rootProject.layout.projectDirectory
        .file("contracts/backend/openapi.yaml")
        .asFile
val recordedResponses =
    rootProject.layout.projectDirectory
        .file("contracts/backend/responses.json")
        .asFile

// Chave de release fora do repositório (README, "Release"). Sem o arquivo, o release sai unsigned em vez de
// falhar: o Generate Signed Bundle / APK do Android Studio injeta a chave por conta própria.
val releaseSigningFile =
    providers
        .gradleProperty("chamados.signingProperties")
        .map { file(it) }
        .getOrElse(file("${providers.systemProperty("user.home").get()}/.config/chamados-android/keystore.properties"))
val releaseSigning =
    releaseSigningFile.takeIf { it.isFile }?.let { props ->
        Properties().apply { props.inputStream().use(::load) }
    }
if (releaseSigning == null) {
    logger.warn("Sem $releaseSigningFile: os APKs release saem sem assinatura (veja README, \"Release\").")
}

android {
    namespace = "br.com.portosoftware.chamados"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.portosoftware.chamados"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    // Debug fala com o backend local com o perfil dev (application-dev.yml), pelo loopback do aparelho e
    // `adb reverse tcp:8181 tcp:8181`: com targetSdk 37, 10.0.2.2 (rede local) exige a permissão
    // ACCESS_LOCAL_NETWORK e, sem ela, a conexão dá timeout. Release fala com a produção; o APK de staging
    // da pipeline passa o endereço dele.
    // Para outro endereço, nos dois: -Pchamados.apiBaseUrl=https://...
    val apiBaseUrl = providers.gradleProperty("chamados.apiBaseUrl")
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.getOrElse("http://127.0.0.1:8181/api/")}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.getOrElse("https://chamados.portosoftware.com.br/api/")}\"")
        }
    }

    // "fake" usa dados simulados; "remote" fala com a API Spring Boot.
    // As telas são as mesmas: só o RepositoryModule de cada flavor muda.
    flavorDimensions += "data"
    productFlavors {
        create("fake") {
            dimension = "data"
            applicationIdSuffix = ".fake"
        }
        create("remote") {
            dimension = "data"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkTestSources = true
        // Avisos de versão nova dependem da rede e não são achados de código.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // Robolectric (SDK 36) acessa internals do JDK 21.
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            if (it.name.contains("Remote")) {
                it.inputs
                    .file(openApiContract)
                    .withPropertyName("openApiContract")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                it.systemProperty("chamados.contract", openApiContract.relativeTo(projectDir).path)
                it.inputs
                    .file(recordedResponses)
                    .withPropertyName("recordedResponses")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                it.systemProperty("chamados.responses", recordedResponses.relativeTo(projectDir).path)
            }
        }
    }
}

ktlint {
    version = libs.versions.ktlint
}

openApiGenerate {
    generatorName = "kotlin"
    library = "jvm-retrofit2"
    inputSpec = rootProject.layout.projectDirectory.file("contracts/backend/openapi.yaml")
    outputDir = layout.buildDirectory.dir("generated/kotlin/openApiGenerate")
    modelPackage = "br.com.portosoftware.chamados.contract.model"
    globalProperties = mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false", "generateMetadata" to "false")
    configOptions =
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "enumUnknownDefaultCase" to "true",
            "dateLibrary" to "string",
            "sourceFolder" to ".",
        )
    typeMappings = mapOf("AnyType" to "JsonElement", "object" to "JsonElement", "UUID" to "kotlin.String")
    importMappings = mapOf("JsonElement" to "kotlinx.serialization.json.JsonElement")
    cleanupOutput = true
    workerIsolation = "process"
    maxWorkerHeapSize = "512m"
}

androidComponents {
    onVariants(selector().withFlavor("data" to "remote")) { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(tasks.named<GenerateTask>("openApiGenerate"), GenerateTask::outputDir)
    }
}

tasks.withType<Detekt>().configureEach {
    val buildDirectory = layout.buildDirectory.get().asFile
    exclude { it.file.startsWith(buildDirectory) }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val generatedClasses =
            tasks.register<Sync>("detekt${name}GeneratedClasses") {
                from(tasks.named<KotlinJvmCompile>("compile${name}Kotlin").flatMap { it.destinationDirectory }) {
                    include("br/com/portosoftware/chamados/contract/model/**")
                }
                from(tasks.named<JavaCompile>("compile${name}JavaWithJavac").flatMap { it.destinationDirectory }) {
                    include("br/com/portosoftware/chamados/BuildConfig.class")
                }
                into(layout.buildDirectory.dir("intermediates/detekt_generated_classes/${variant.name}"))
            }
        tasks.withType<Detekt>().matching { it.name == "detekt$name" }.configureEach {
            classpath.from(tasks.named<KotlinJvmCompile>("compile${name}Kotlin").map { it.libraries }, generatedClasses)
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    parallel = true
}

dependencies {
    detektPlugins(libs.compose.rules.detekt)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.paging.common)
    testImplementation(libs.robolectric)
    "testRemoteImplementation"(libs.okhttp.mockwebserver)
    "testRemoteImplementation"(libs.snakeyaml)
    testImplementation(libs.hilt.android.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    kspTest(libs.hilt.compiler)
}
