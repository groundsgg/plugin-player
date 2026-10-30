plugins {
    id("gg.grounds.kotlin-conventions")
    id("com.github.gmazzo.buildconfig")
}

buildConfig {
    className("BuildInfo")
    packageName("gg.grounds.player.settings.minestom")
    useKotlinOutput()
    buildConfigField("String", "VERSION", "\"${project.version}\"")
}

repositories {
    // Minestom for Minecraft 26.3 comes from our fork until upstream releases it.
    maven {
        url = uri("https://maven.pkg.github.com/groundsgg/minestom")
        credentials {
            username = providers.gradleProperty("github.user").get()
            password = providers.gradleProperty("github.token").get()
        }
        content { includeGroup("net.minestom") }
    }
    maven {
        url = uri("https://maven.pkg.github.com/groundsgg/*")
        credentials {
            username = providers.gradleProperty("github.user").get()
            password = providers.gradleProperty("github.token").get()
        }
    }
}

dependencies {
    implementation(project(":common"))
    api("gg.grounds:grounds-minestom-runtime-runtime-api:0.4.0")
    // The version the lobby and game images run (Minecraft 26.3, groundsgg fork). compileOnly:
    // the host brings Minestom, and compiling against an older API is how a NoSuchMethodError
    // gets shipped.
    compileOnly("net.minestom:minestom:2026.10.02-26.3-grounds.1")
    // library-i18n renders MiniMessage, which Minestom does not bring, so it ships with the module.
    // The version tracks Minestom's own Adventure.
    implementation("net.kyori:adventure-text-minimessage:5.2.0")

    testImplementation("net.minestom:minestom:2026.10.02-26.3-grounds.1")
    // DefaultServiceRegistry, for a GroundsServerContext in tests.
    testImplementation("gg.grounds:library-jvm-modules-module-core:0.1.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}
