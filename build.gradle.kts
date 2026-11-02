plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}
group = "dev.clipboard.sync"
version = "0.3.0"
repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    intellijPlatform {
        val localPath = providers.gradleProperty("localIdePath")
        if (localPath.isPresent) local(localPath.get()) else intellijIdeaCommunity("2025.1.2")
        pluginVerifier()
    }
}
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(21) }
tasks.test { useJUnitPlatform() }
intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "251"; untilBuild = "253.*" }
    }
    pluginVerification {
        ides {
            create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.2")
            create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea, "2025.3.3")
        }
    }
}
