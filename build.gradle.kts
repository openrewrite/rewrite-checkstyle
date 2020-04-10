import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm") version "1.3.71"
    id("nebula.release") version "13.2.1"
    id("nebula.maven-publish") version "17.2.1"
    id("nebula.maven-resolved-dependencies") version "17.2.1"
}

group = "org.gradle.rewrite.plan"
description = "Refactor checkstyle automatically"

repositories {
    maven { url = uri("http://oss.jfrog.org/oss-snapshot-local") }

    maven {
        url = uri("https://repo.gradle.org/gradle/enterprise-libs-releases-local/")
        credentials  {
            username = project.findProperty("artifactoryUsername") as String
            password = project.findProperty("artifactoryPassword") as String
        }
        authentication {
            create<BasicAuthentication>("basic")
        }
    }
    maven {
        url = uri("https://repo.gradle.org/gradle/enterprise-libs-snapshots-local/")
        credentials  {
            username = project.findProperty("artifactoryUsername") as String
            password = project.findProperty("artifactoryPassword") as String
        }
        authentication {
            create<BasicAuthentication>("basic")
        }
    }

    jcenter {
        content {
            excludeVersionByRegex("com\\.fasterxml\\.jackson\\..*", ".*", ".*rc.*")
        }
    }

    jcenter {
        content {
            includeVersionByRegex("com\\.fasterxml\\.jackson\\..*", ".*", "(\\d+\\.)*\\d+")
        }
    }
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
}

java {
    withSourcesJar()
}

dependencies {
    implementation("org.gradle.rewrite:rewrite-java:latest.integration")

    implementation("com.puppycrawl.tools:checkstyle:latest.release")

    // FIXME the IDE throws "unknown enum constant com.fasterxml.jackson.annotation.JsonTypeInfo.Id.MINIMAL_CLASS sometimes?
    implementation("com.fasterxml.jackson.core:jackson-annotations:latest.release")

    implementation("commons-cli:commons-cli:1.4")

    implementation("io.micrometer.prometheus:prometheus-rsocket-client:latest.release")
    implementation("io.rsocket:rsocket-transport-netty:1.0.0-RC7-SNAPSHOT")

    implementation("ch.qos.logback:logback-classic:1.0.13")

    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")

    testImplementation("org.assertj:assertj-core:latest.release")
}

tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(listOf("Main-Class" to "org.gradle.rewrite.checkstyle.Main").toMap())
    }
}

fun shouldUseReleaseRepo(): Boolean {
    return project.gradle.startParameter.taskNames.contains("final") || project.gradle.startParameter.taskNames.contains(":final")
}

project.gradle.taskGraph.whenReady(object: Action<TaskExecutionGraph> {
    override fun execute(graph: TaskExecutionGraph) {
            if (graph.hasTask(":snapshot") || graph.hasTask(":immutableSnapshot")) {
                throw GradleException("You cannot use the snapshot or immutableSnapshot task from the release plugin. Please use the devSnapshot task.")
            }
    }
})

publishing {
    repositories {
        maven {
            name = "GradleEnterprise"
            url = if(shouldUseReleaseRepo()) {
                URI.create("https://repo.gradle.org/gradle/enterprise-libs-releases-local")
            } else {
                URI.create("https://repo.gradle.org/gradle/enterprise-libs-snapshots-local")
            }
            credentials {
                username = project.findProperty("artifactoryUsername") as String?
                password = project.findProperty("artifactoryPassword") as String?
            }
        }
    }
}

project.rootProject.tasks.getByName("postRelease").dependsOn(project.tasks.getByName("publishNebulaPublicationToGradleEnterpriseRepository"))