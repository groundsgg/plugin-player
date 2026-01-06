plugins {
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    implementation(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set(rootProject.name)
    archiveClassifier.set("") // Removes the 'all' classifier
    archiveVersion.set("") // Removes the version from the jar name
}
