plugins { id("gg.grounds.velocity") version "0.1.1" }

repositories { mavenCentral() }

dependencies {
    implementation(project(":common"))
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.0.3")
    implementation("tools.jackson.module:jackson-module-kotlin:3.0.3")
}
