import java.net.URI
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun localString(name: String): String =
    localProperties.getProperty(name, "").replace("\\", "\\\\").replace("\"", "\\\"")

val releaseSigningProperties = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val releaseSigningFields = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigning = releaseSigningFields.all {
    !releaseSigningProperties.getProperty(it).isNullOrBlank()
}

val verifyReleaseConfiguration = tasks.register("verifyReleaseConfiguration") {
    group = "verification"
    description = "Verifica el destino explícito de release y la configuración de firma sin mostrar credenciales."
    doLast {
        val url = localProperties.getProperty("RELEASE_SUPABASE_URL", "")
        val redirect = localProperties.getProperty("RELEASE_AUTH_REDIRECT_URL", "")
        val key = localProperties.getProperty("RELEASE_SUPABASE_PUBLISHABLE_KEY", "")
        fun isHttpsUrl(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)
        check(isHttpsUrl(url)) { "Configura RELEASE_SUPABASE_URL con el destino HTTPS de la entrega." }
        check(isHttpsUrl(redirect)) { "Configura RELEASE_AUTH_REDIRECT_URL con la recuperación HTTPS autorizada." }
        val isAnonJwt = runCatching {
            val payload = String(Base64.getUrlDecoder().decode(key.split('.')[1]))
            Regex("\"role\"\\s*:\\s*\"anon\"").containsMatchIn(payload)
        }.getOrDefault(false)
        check(key.startsWith("sb_publishable_") || isAnonJwt) {
            "Release requiere una clave publicable/anon; nunca una clave administrativa."
        }
        check(hasReleaseSigning) { "Configura la firma existente en key.properties (archivo ignorado por Git)." }
        check(rootProject.file(releaseSigningProperties.getProperty("storeFile")).isFile) {
            "No se encuentra el almacén de firma configurado."
        }
    }
}

android {
    namespace = "com.intutec.viveroapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.intutec.viveroapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"${localString("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${localString("SUPABASE_PUBLISHABLE_KEY")}\"")
        buildConfigField("String", "AUTH_REDIRECT_URL", "\"${localString("AUTH_REDIRECT_URL")}\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            buildConfigField("String", "SUPABASE_URL", "\"${localString("RELEASE_SUPABASE_URL")}\"")
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${localString("RELEASE_SUPABASE_PUBLISHABLE_KEY")}\"")
            buildConfigField("String", "AUTH_REDIRECT_URL", "\"${localString("RELEASE_AUTH_REDIRECT_URL")}\"")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }.configureEach {
    dependsOn(verifyReleaseConfiguration)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.zxing.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.register<JavaExec>("generateQrLabels") {
    group = "verification"
    description = "Genera una hoja HTML imprimible de etiquetas QR desde un CSV."
    dependsOn("compileDebugUnitTestKotlin")
    classpath = configurations.getByName("debugUnitTestRuntimeClasspath") +
        files(
            layout.buildDirectory.dir(
                "intermediates/built_in_kotlinc/debugUnitTest/" +
                    "compileDebugUnitTestKotlin/classes",
            ),
        )
    mainClass.set("com.intutec.viveroapp.tools.QrLabelGenerator")

    doFirst {
        val inputPath = providers.gradleProperty("qrLabelsFile")
            .orElse(rootProject.file("docs/qr-labels-sample.csv").absolutePath)
            .get()
        val outputPath = providers.gradleProperty("qrLabelsOutput")
            .orElse(layout.buildDirectory.file("qr-labels/labels.html").get().asFile.absolutePath)
            .get()
        args(inputPath, outputPath)
    }
}
