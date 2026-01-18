plugins {
    id("gg.grounds.kotlin-conventions")
    id("com.google.protobuf") version "0.9.6"
}

val grpcVersion = "1.78.0"
val protobufVersion = "4.33.4"

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
    api("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    protobuf("gg.grounds:library-grpc-contracts-player:0.1.0")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }

    plugins { create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" } }

    generateProtoTasks { all().forEach { task -> task.plugins { create("grpc") } } }
}
