plugins { id("gg.grounds.velocity-conventions") }

dependencies {
    implementation(project(":common"))
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.0.3")
    implementation("tools.jackson.module:jackson-module-kotlin:3.0.3")
    implementation("io.grpc:grpc-netty-shaded:1.78.0")
}
