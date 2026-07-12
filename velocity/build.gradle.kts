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
    compileOnly("gg.grounds:plugin-proxy-api:0.1.0")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.0.4")
    implementation("tools.jackson.module:jackson-module-kotlin:3.0.4")
    implementation("io.grpc:grpc-netty-shaded:1.78.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
