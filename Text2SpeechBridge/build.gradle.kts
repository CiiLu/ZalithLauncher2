import java.security.MessageDigest

plugins {
    java
}

group = "com.mojang.text2speech"

fun writeVersion(file: File, jarFile: File) {
    val digest = MessageDigest.getInstance("SHA-1")
    jarFile.inputStream().use { stream ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    file.writeText(digest.digest().joinToString("") { "%02x".format(it) })
}

val versionFile = file("$rootDir/ZalithLauncher/src/main/assets/app_runtime/tts_bridge/version")

tasks.jar {
    archiveBaseName.set("text2speech-bridge")
    destinationDirectory.set(file("$rootDir/ZalithLauncher/src/main/assets/app_runtime/tts_bridge"))

    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    doLast {
        writeVersion(versionFile, archiveFile.get().asFile)
    }
    outputs.file(versionFile)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
