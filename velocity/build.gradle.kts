import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins { id("gg.grounds.velocity-conventions") }

dependencies {
    implementation(project(":common"))
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.0.3")
    implementation("tools.jackson.module:jackson-module-kotlin:3.0.3")
    runtimeOnly("io.grpc:grpc-netty-shaded:1.78.0")
}

tasks.withType<ShadowJar>().configureEach { mergeServiceFiles() }
