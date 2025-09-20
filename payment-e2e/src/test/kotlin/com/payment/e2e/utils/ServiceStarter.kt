package com.payment.e2e.utils

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ServiceStarter {

    companion object {
        private val processes = mutableMapOf<String, Process>()

        fun startPaymentBackend() {
            println("Starting Payment Backend Service on port 8080...")

            // Start the service as a separate process
            val process = startServiceProcess(
                "payment-backend",
                8080,
                mapOf(
                    "SPRING_DATASOURCE_URL" to "jdbc:postgresql://localhost:5432/payment_db",
                    "SPRING_DATASOURCE_USERNAME" to "payment_user",
                    "SPRING_DATASOURCE_PASSWORD" to "payment_password",
                    "SPRING_KAFKA_BOOTSTRAP_SERVERS" to "localhost:9092",
                    "SPRING_JPA_HIBERNATE_DDL_AUTO" to "create-drop",
                    "SPRING_FLYWAY_ENABLED" to "false",
                    "JWT_SECRET" to "testSecretKey123456789012345678901234567890"
                )
            )

            processes["payment-backend"] = process

            // Wait for service to be ready
            waitForService("http://localhost:8080/actuator/health", 2.minutes)
            println("Payment Backend Service started successfully")
        }

        fun startBalanceService() {
            println("Starting Balance Service on port 8081...")

            // Start the service as a separate process
            val process = startServiceProcess(
                "payment-balance-service",
                8081,
                mapOf(
                    "SPRING_DATASOURCE_URL" to "jdbc:postgresql://localhost:5432/balance_db",
                    "SPRING_DATASOURCE_USERNAME" to "balance_user",
                    "SPRING_DATASOURCE_PASSWORD" to "balance_password",
                    "SPRING_KAFKA_BOOTSTRAP_SERVERS" to "localhost:9092",
                    "SPRING_JPA_HIBERNATE_DDL_AUTO" to "create-drop",
                    "SPRING_FLYWAY_ENABLED" to "false",
                    "PAYMENT_BACKEND_URL" to "http://localhost:8080"
                )
            )

            processes["payment-balance-service"] = process

            // Wait for service to be ready
            waitForService("http://localhost:8081/actuator/health", 2.minutes)
            println("Balance Service started successfully")
        }

        private fun startServiceProcess(
            serviceName: String,
            port: Int,
            environment: Map<String, String>
        ): Process {
            val projectRoot = File("/Users/maximus/IdeaProjects/payment-task")

            // Build the service first
            println("Building $serviceName...")
            val buildProcess = ProcessBuilder(
                "./gradlew",
                ":$serviceName:build",
                "-x", "test"
            ).directory(projectRoot)
                .redirectErrorStream(true)
                .start()

            buildProcess.waitFor()

            // Start the service
            val processBuilder = ProcessBuilder(
                "./gradlew",
                ":$serviceName:bootRun"
            ).directory(projectRoot)

            // Set environment variables
            val processEnv = processBuilder.environment()
            processEnv["SERVER_PORT"] = port.toString()
            environment.forEach { (key, value) ->
                processEnv[key] = value
            }

            // Redirect output to files for debugging
            val logDir = File(projectRoot, "build/test-logs")
            logDir.mkdirs()
            processBuilder.redirectOutput(File(logDir, "$serviceName.log"))
            processBuilder.redirectError(File(logDir, "$serviceName-error.log"))

            return processBuilder.start()
        }

        private fun waitForService(healthUrl: String, timeout: Duration) {
            val endTime = System.currentTimeMillis() + timeout.inWholeMilliseconds
            var lastException: Exception? = null

            while (System.currentTimeMillis() < endTime) {
                try {
                    val url = URL(healthUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    if (connection.responseCode == 200 || connection.responseCode == 503) {
                        // Service is up (503 might mean dependencies not ready but service is running)
                        Thread.sleep(5000) // Give it a bit more time to fully initialize
                        return
                    }
                } catch (e: Exception) {
                    lastException = e
                }

                Thread.sleep(2000)
            }

            throw RuntimeException(
                "Service at $healthUrl did not start within $timeout",
                lastException
            )
        }

        fun stopAllServices() {
            println("Stopping all services...")

            processes.forEach { (name, process) ->
                try {
                    println("Stopping $name process...")
                    process.destroy()
                    if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                } catch (e: Exception) {
                    println("Error stopping $name process: ${e.message}")
                }
            }

            processes.clear()

            println("All services stopped")
        }
    }
}