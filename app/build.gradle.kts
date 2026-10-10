import java.net.URI
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
        val url = localProperties.getProperty("RELEASE_BACKEND_API_URL", "")
        fun isHttpsOrigin(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.path in listOf("", "/") && uri.query == null && uri.fragment == null
        }.getOrDefault(false)
        check(isHttpsOrigin(url)) { "Configura RELEASE_BACKEND_API_URL con el origen HTTPS de la entrega." }
        val web = localProperties.getProperty("RELEASE_BACKEND_WEB_URL", "")
        check(web.isBlank() || isHttpsOrigin(web)) { "RELEASE_BACKEND_WEB_URL debe ser un origen HTTPS." }
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
        versionCode = 9
        versionName = "1.0.8-vps"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Public origin only; credentials and sessions never belong in BuildConfig.
        buildConfigField("String", "BACKEND_API_URL", "\"${localString("BACKEND_API_URL")}\"")
        buildConfigField("String", "BACKEND_WEB_URL", "\"${localString("BACKEND_WEB_URL")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"\"")
        buildConfigField("String", "AUTH_REDIRECT_URL", "\"\"")
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
            buildConfigField("String", "BACKEND_API_URL", "\"${localString("RELEASE_BACKEND_API_URL")}\"")
            buildConfigField("String", "BACKEND_WEB_URL", "\"${localString("RELEASE_BACKEND_WEB_URL")}\"")
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
