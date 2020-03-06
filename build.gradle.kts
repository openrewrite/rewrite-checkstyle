import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.net.URI

plugins {
    `java-library`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
    id("nebula.release") version "13.2.1"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

group = "org.gradle"
description = "Refactor checkstyle automatically"

repositories {
    maven { url = uri("https://oss.jfrog.org/artifactory/oss-snapshot-local") }
    mavenCentral()
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
}

dependencies {
    implementation("com.netflix.devinsight.rewrite:rewrite-java:latest.integration")

    implementation("com.puppycrawl.tools:checkstyle:latest.release")

    // FIXME the IDE throws "unknown enum constant com.fasterxml.jackson.annotation.JsonTypeInfo.Id.MINIMAL_CLASS sometimes?
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.10.2")

    implementation("commons-cli:commons-cli:1.4")

    runtimeOnly("ch.qos.logback:logback-classic:1.0.13")

    compileOnly("org.projectlombok:lombok:1.18.10")
    annotationProcessor("org.projectlombok:lombok:1.18.10")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")

    testImplementation("org.assertj:assertj-core:latest.release")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
}

val shadowJar = tasks.named<ShadowJar>("shadowJar")

publishing {
    publications {
        create<MavenPublication>("runnableJar") {
            artifactId = "rewrite-checkstyle"
            artifact(shadowJar.get()) {
                classifier = null
            }
        }
    }
    repositories {
        maven {
            name = "GradleSnapshots"
            url = URI.create("https://repo.gradle.org/gradle/libs-snapshots-local")
            credentials {
                username = project.findProperty("artifactoryUsername") as String?
                password = project.findProperty("artifactoryPassword") as String?
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("jar") {
            artifactId = "rewrite-checkstyle"
        }
    }
    repositories {
        maven {
            name = "GradleBuildInternalSnapshots"
            url = URI.create("https://repo.gradle.org/gradle/libs-snapshots-local")
            credentials {
                username = project.findProperty("artifactoryUsername") as String?
                password = project.findProperty("artifactoryPassword") as String?
            }
        }
    }
}