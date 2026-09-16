plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
}

group = "cn.campus.schedule"
version = "0.14.0"

kotlin { jvmToolchain(17) }
application { mainClass.set("cn.campus.community.ApplicationKt") }

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.1.3")
    implementation("io.ktor:ktor-server-netty-jvm:3.1.3")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.1.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.1.3")
    implementation("io.ktor:ktor-server-auth-jvm:3.1.3")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.1.3")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.1.3")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.1.3")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("com.mysql:mysql-connector-j:9.3.0")
    implementation("org.flywaydb:flyway-core:11.8.1")
    implementation("org.flywaydb:flyway-mysql:11.8.1")
    implementation("de.mkammerer:argon2-jvm:2.11")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.1.3")
}

tasks.test { useJUnitPlatform() }
