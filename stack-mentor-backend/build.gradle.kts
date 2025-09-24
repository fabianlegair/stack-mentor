plugins {
    id("java")
    id("org.springframework.boot") version "4.0.0-M3"
    id("io.spring.dependency-management") version "1.1.0"
}

group = "io.stackmentor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:4.0.0-M3")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:4.0.0-M3")
    implementation("org.springframework.boot:spring-boot-starter-security:4.0.0-M3")
    implementation("org.springframework.boot:spring-boot-starter-websocket:4.0.0-M3")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("org.flywaydb:flyway-core:11.13.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}