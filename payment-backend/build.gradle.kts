plugins {
    kotlin("plugin.jpa")
    id("com.google.cloud.tools.jib") version "3.4.0"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")

    implementation("org.postgresql:postgresql")

    // JWT dependencies
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.6")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

// Jib configuration for Docker image building
jib {
    from {
        image = "eclipse-temurin:21-jre-alpine"
    }
    to {
        image = "ghcr.io/kochetkov-ma/payment-task/payment-backend"
        tags = setOf("${project.version}", "latest")
    }
    container {
        ports = listOf("8080")
        jvmFlags = listOf(
            "-Djava.security.egd=file:/dev/./urandom",
            "-XX:+UseContainerSupport",
            "-XX:MaxRAMPercentage=75.0"
        )
    }
}

// Task to build and push multi-arch Docker image to GHCR
tasks.register("buildMultiArchImage") {
    group = "docker"
    description = "Build and push multi-arch Docker image to GitHub Container Registry"
    dependsOn("bootJar")
    doLast {
        val dockerPath = "/Applications/Docker.app/Contents/Resources/bin/docker"

        // Use existing builder or create new one
        val result = exec {
            commandLine(dockerPath, "buildx", "use", "multiarch-builder")
            isIgnoreExitValue = true
        }

        // If builder doesn't exist, create it
        if (result.exitValue != 0) {
            exec {
                commandLine(dockerPath, "buildx", "create", "--name", "multiarch-builder", "--driver", "docker-container", "--bootstrap", "--use")
            }
        }

        // Build and push multi-arch image
        exec {
            commandLine(
                dockerPath, "buildx", "build",
                "--platform", "linux/amd64,linux/arm64",
                "--tag", "ghcr.io/kochetkov-ma/payment-task/payment-backend:${project.version}",
                "--tag", "ghcr.io/kochetkov-ma/payment-task/payment-backend:latest",
                "--push",
                "."
            )
        }
    }
}