plugins {
    `java-library`
    id("xyz.jpenilla.run-paper") version "2.3.1" // Paper server for testing/hotloading JVM
    id("io.github.goooler.shadow") version "8.1.7"
}

group = "xyz.marroq"
version = "0.0.1"
description = "when a homeblock or an outpost chunk is captured all connected chunks are transferred to the captor as an outpost"
java.sourceCompatibility = JavaVersion.VERSION_21
var mainMinecraftVersion = "1.20.1"

repositories {
    mavenLocal()
    mavenCentral()

    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://jitpack.io")

    // Paper
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$mainMinecraftVersion-R0.1-SNAPSHOT")

    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
    compileOnly("com.github.TownyAdvanced:FlagWar:0.6.4")
    compileOnly("com.github.TownyAdvanced.Towny:towny:0.101.1.9")
    compileOnly("dev.glowie:townyspaceports:0.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.107.0")
}

tasks {
    processResources {
        val props = mapOf(
            "name" to project.name,
            "version" to project.version,
            "description" to project.description,
        )
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        val prefix = "${project.group}.lib"
        sequenceOf(
            "co.aikar",
        ).forEach { pkg ->
            relocate(pkg, "$prefix.$pkg")
        }

        archiveFileName.set("${project.name}-${project.version}.jar")
    }
    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(mainMinecraftVersion)
    }

    test {
        useJUnitPlatform()
    }
}

@Suppress("UnstableApiUsage")
tasks.withType(xyz.jpenilla.runtask.task.AbstractRun::class) {
    javaLauncher = javaToolchains.launcherFor {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(21)
    }
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}
