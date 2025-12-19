import org.gradle.jvm.tasks.Jar

plugins {
    application
    java
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
    // no JavaFX Maven deps – use local SDK
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.register<Jar>("clientFatJar") {
    archiveBaseName.set("S1ghtRe4dersClient")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })

    manifest {
        attributes["Main-Class"] = "com.s1ghtre4ders.client.LobbyClient"
    }

    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}
