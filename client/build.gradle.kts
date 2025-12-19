import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.s1ghtre4ders.client.LobbyClient")
}

repositories {
    mavenCentral()
}

dependencies {
    val javafxVersion = "21.0.9"

    implementation("org.openjfx:javafx-base:$javafxVersion:win")
    implementation("org.openjfx:javafx-controls:$javafxVersion:win")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:win")
    implementation("org.openjfx:javafx-fxml:$javafxVersion:win")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.s1ghtre4ders.client.LobbyClient"
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("s1ghtre4ders-client")
    archiveVersion.set("")
    archiveClassifier.set("")
}