plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.proyecto"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.proyecto"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//        ndk {
//            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
//        }
        //para la arquitectura de android
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a")
                isUniversalApk = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true //false anteriormente
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        //para el emulador
//        getByName("debug") {
//            ndk {
//                abiFilters += listOf("x86", "x86_64")
//            }
//        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/*.kotlin_module"
        }
        // Evita que Android comprima el modelo para que PyTorch pueda leerlo directamente
        pickFirst("lib/**/libc++_shared.so")
    }

    // Esto es vital para archivos .pt o .ptl en assets o raw
    aaptOptions {
        noCompress("ptl", "pt")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.play.services.maps3d)
    implementation(libs.androidx.tools.core)
    implementation(libs.litert.support.api)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    //mis dependencias
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    val camerax_version = "1.3.4"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
    implementation("io.coil-kt:coil-compose:2.6.0")
    val room_version = "2.8.4"
    implementation("androidx.room:room-runtime:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    // Para Compose
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Extensión para GIFs
    implementation("io.coil-kt:coil-gif:2.6.0")
    //Para el modelo de IA
    // Core de PyTorch para Android (Lite Interpreter)
    implementation("org.pytorch:pytorch_android_lite:1.13.1")

    // Utilidades para manejo de Bitmaps y Tensores (NCHW)
    implementation("org.pytorch:pytorch_android_torchvision_lite:1.13.1")

}
