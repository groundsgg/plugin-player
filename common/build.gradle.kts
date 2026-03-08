plugins { id("gg.grounds.grpc-conventions") }

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/groundsgg/*")
        credentials {
            username = providers.gradleProperty("github.user").get()
            password = providers.gradleProperty("github.token").get()
        }
    }
}

dependencies { protobuf("gg.grounds:library-grpc-contracts-player:feat-player-heartbeat-SNAPSHOT") }
