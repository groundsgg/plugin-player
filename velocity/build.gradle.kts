plugins { id("gg.grounds.velocity-conventions") }

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
    implementation(project(":common"))
    // plugin-proxy owns the ProxyServiceRegistry at runtime — compileOnly, never shaded, or the
    // registry this plugin writes into would be a different class from the one chat/social read.
    compileOnly("gg.grounds:plugin-proxy-api:0.5.0")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.0.4")
    implementation("tools.jackson.module:jackson-module-kotlin:3.0.4")

    // compileOnly above is not visible to tests; PlayerSessionQueryImplTest needs the interface's
    // types.
    testImplementation("gg.grounds:plugin-proxy-api:0.5.0")
    // Same reason: the conventions plugin puts velocity-api on compileOnly, and
    // EditionStampListenerTest builds a GameProfile.Property.
    testImplementation("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}
