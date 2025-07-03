plugins {
    id("com.google.protobuf").version("0.9.4")
    id("me.champeau.jmh").version("0.7.2")
}

// https://docs.gradle.org/current/userguide/java_testing.html#ex-disable-publishing-of-test-fixtures-variants
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }

dependencies {
    api("io.netty:netty-common")
    api("io.netty:netty-buffer")
    api("io.netty:netty-transport")
    implementation("io.netty:netty-handler")
    implementation("io.netty:netty-codec-http")
    implementation("io.netty:netty-transport-classes-epoll")
    // fixes QuicChannelBootstrap language problem for Android API <= 23
    implementation("com.github.vazh2100:netty-incubator-codec-classes-quic:94e43f2732")
    implementation("io.netty.incubator:netty-incubator-codec-native-quic"){
        exclude("io.netty.incubator", module = "netty-incubator-codec-classes-quic")
    }

    api("com.google.protobuf:protobuf-java")

    implementation("com.github.multiformats:java-multibase")
    implementation("tech.pegasys:noise-java")

    implementation("org.bouncycastle:bcprov-jdk18on")
    implementation("org.bouncycastle:bcpkix-jdk18on")

    testImplementation(project(":tools:schedulers"))

    testImplementation("io.netty.incubator:netty-incubator-codec-native-quic::linux-x86_64")
    testImplementation("io.netty.incubator:netty-incubator-codec-native-quic::linux-aarch_64")
    testImplementation("io.netty.incubator:netty-incubator-codec-native-quic::osx-x86_64")
    testImplementation("io.netty.incubator:netty-incubator-codec-native-quic::osx-aarch_64")
    testImplementation("io.netty.incubator:netty-incubator-codec-native-quic::windows-x86_64")
    testImplementation("io.netty:netty-tcnative-boringssl-static:2.0.70.Final:linux-x86_64")
    testImplementation("io.netty:netty-tcnative-boringssl-static:2.0.70.Final:linux-aarch_64")
    testImplementation("io.netty:netty-tcnative-boringssl-static:2.0.70.Final:osx-x86_64")
    testImplementation("io.netty:netty-tcnative-boringssl-static:2.0.70.Final:osx-aarch_64")
    testImplementation("io.netty:netty-tcnative-boringssl-static:2.0.70.Final:windows-x86_64")

    testFixturesApi("org.apache.logging.log4j:log4j-core")
    testFixturesImplementation(project(":tools:schedulers"))
    testFixturesImplementation("io.netty:netty-transport-classes-epoll")
    testFixturesImplementation("io.netty:netty-handler")
    testFixturesImplementation("org.junit.jupiter:junit-jupiter-api")

    jmhImplementation(project(":tools:schedulers"))
    jmhImplementation("org.openjdk.jmh:jmh-core")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc"
    }

    tasks["clean"].doFirst { delete(generatedFilesBaseDir) }

    idea {
        module {
            sourceDirs.add(file("$generatedFilesBaseDir/main/java"))
        }
    }
}
