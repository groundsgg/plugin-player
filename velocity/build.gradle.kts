plugins { id("gg.grounds.velocity-conventions") }

dependencies {
    implementation(project(":common"))
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.0.4")
    implementation("tools.jackson.module:jackson-module-kotlin:3.0.4")
    implementation("io.grpc:grpc-netty-shaded:1.78.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
