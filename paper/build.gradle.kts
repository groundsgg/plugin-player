plugins { id("gg.grounds.paper-base-conventions") }

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
    // The buildserver runs Paper 26.3. Not paper-conventions: that pins the 26.1 API.
    compileOnly("io.papermc.paper:paper-api:26.3.build.214-stable")
}

val pluginVersion: Any = project.version

tasks.withType<ProcessResources> {
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") { expand(mapOf("VERSION" to pluginVersion)) }
}
