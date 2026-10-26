plugins { id("gg.grounds.kotlin-conventions") }

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/groundsgg/*")
        credentials {
            username = providers.gradleProperty("github.user").get()
            password = providers.gradleProperty("github.token").get()
        }
    }
}

dependencies {
    // service-player is reached over HTTP now: the JDK's own client, and Jackson for the bodies —
    // the same pair ForgeLinkClient already uses, so nothing new lands in the shaded jar.
    implementation("tools.jackson.core:jackson-databind:3.2.3")

    // The settings menu. Its text goes through library-i18n; api, as SettingsMenu.messages returns
    // it.
    api("gg.grounds:library-i18n:0.4.4")
    // compileOnly: Minestom, Paper and Velocity each ship their own Adventure, Gson and SLF4J, and
    // a shaded copy of Adventure is a second Component type the host rejects. Compiled against
    // Paper's Adventure 4; the tests run it on Minestom's 5, which is what the lobby and games use.
    compileOnly("net.kyori:adventure-api:5.2.0")
    compileOnly("net.kyori:adventure-text-serializer-plain:5.2.0")
    compileOnly("com.google.code.gson:gson:2.13.2")
    compileOnly("org.slf4j:slf4j-api:2.0.17")

    testImplementation("net.kyori:adventure-api:5.2.0")
    testImplementation("net.kyori:adventure-text-serializer-plain:5.2.0")
    testImplementation("net.kyori:adventure-text-minimessage:5.2.0")
    testImplementation("com.google.code.gson:gson:2.13.2")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}
