plugins { id("gg.grounds.kotlin-conventions") }

dependencies {
    // service-player is reached over HTTP now: the JDK's own client, and Jackson for the bodies —
    // the same pair ForgeLinkClient already uses, so nothing new lands in the shaded jar.
    implementation("tools.jackson.core:jackson-databind:3.0.4")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
