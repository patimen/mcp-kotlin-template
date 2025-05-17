plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("plugin.serialization") version "2.1.0"
    id("sh.ondr.koja") version "0.3.2"
}

group = "com.ame"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk:0.5.0")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

// Set the main class for the application
application {
    mainClass.set("com.ame.MainKt")
}

// Configure the shadow JAR
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("mcp-kotlin-template")
    archiveClassifier.set("")
    archiveVersion.set(version.toString())
    mergeServiceFiles()

    manifest {
        attributes(mapOf(
            "Main-Class" to "com.ame.MainKt"
        ))
    }
}
tasks.named("distZip") {
    dependsOn(tasks.named("shadowJar"))
}
tasks.named("distTar") {
    dependsOn(tasks.named("shadowJar"))
}
tasks.named("startScripts") {
    dependsOn(tasks.named("shadowJar"))
}
tasks.named("startShadowScripts") {
    dependsOn(tasks.named("jar"))
}
// Make the build task depend on the shadowJar task
tasks.build {
    dependsOn(tasks.shadowJar)
}

// Create a task to run the application
tasks.register<JavaExec>("runApp") {
    group = "application"
    description = "Run the application from the command line"
    mainClass.set("com.ame.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}
