// Disable bootJar task since this module is for testing only
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

// Configure test task
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // Increase timeout for E2E tests
    systemProperty("junit.jupiter.execution.timeout.default", "5 m")
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")

    // REST Assured for API testing
    testImplementation("io.rest-assured:rest-assured:5.3.2")
    testImplementation("io.rest-assured:kotlin-extensions:5.3.2")
    testImplementation("io.rest-assured:json-path:5.3.2")
    testImplementation("io.rest-assured:xml-path:5.3.2")

    // Additional test utilities - Using standalone version for Jakarta support
    testImplementation("com.github.tomakehurst:wiremock-standalone:3.0.1")

    // PostgreSQL driver for direct database access in tests
    testImplementation("org.postgresql:postgresql")

    // Jackson for JSON serialization in tests
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}