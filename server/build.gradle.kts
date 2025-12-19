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
    mainClass.set("com.s1ghtre4ders.server.LobbyServer")
}

repositories {
    mavenCentral()
}

dependencies {
    // (no extra deps yet – pure Java)
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.s1ghtre4ders.server.LobbyServer"
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
