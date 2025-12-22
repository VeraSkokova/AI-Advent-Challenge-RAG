plugins {
    kotlin("jvm") version "2.0.20" // Используем стабильную версию
    kotlin("plugin.serialization") version "2.0.20"
    id("application")
}

group = "ru.skokova.aiadventchallenge.rag"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Client 3.0.0 (как в day15)
    implementation("io.ktor:ktor-client-core:3.0.0")
    implementation("io.ktor:ktor-client-cio:3.0.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
    implementation("io.ktor:ktor-client-logging:3.0.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")

    // JSON сериализация
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Логирование
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("ru.skokova.aiadventchallenge.rag.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}