plugins { id("gg.grounds.kotlin-conventions") }

dependencies {
    // service-player is reached over HTTP now: the JDK's own client, and Jackson for the bodies —
    // the same pair ForgeLinkClient already uses, so nothing new lands in the shaded jar.
    implementation("tools.jackson.core:jackson-databind:3.2.2")

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}
