package com.payment.e2e

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.HttpURLConnection
import java.net.URL
import java.sql.DriverManager

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicInfrastructureTest {

    @BeforeAll
    fun setup() {
        println("=== Testing Infrastructure Connectivity ===")
        println("Note: Make sure to run 'docker compose up -d' before running tests")
        println("Expected services:")
        println("  - PostgreSQL: localhost:5432")
        println("  - Kafka: localhost:9092")
        println("  - WireMock: localhost:8082")
    }

    @Test
    @Order(1)
    @DisplayName("PostgreSQL databases are accessible at localhost:5432")
    fun testPostgreSQLConnection() {
        println("\n=== Testing PostgreSQL Connection ===")

        // Test payment database connection
        val paymentJdbcUrl = "jdbc:postgresql://localhost:5432/payment_db"
        println("Testing payment database at: $paymentJdbcUrl")

        val paymentConnection = DriverManager.getConnection(
            paymentJdbcUrl,
            "payment_user",
            "payment_password"
        )

        assertNotNull(paymentConnection)
        assertFalse(paymentConnection.isClosed)

        val paymentStatement = paymentConnection.createStatement()
        val paymentResult = paymentStatement.executeQuery("SELECT 1")
        assertTrue(paymentResult.next())
        assertEquals(1, paymentResult.getInt(1))

        paymentConnection.close()
        println("Payment database connection successful")

        // Test balance database connection
        val balanceJdbcUrl = "jdbc:postgresql://localhost:5432/balance_db"
        println("Testing balance database at: $balanceJdbcUrl")

        val balanceConnection = DriverManager.getConnection(
            balanceJdbcUrl,
            "balance_user",
            "balance_password"
        )

        assertNotNull(balanceConnection)
        assertFalse(balanceConnection.isClosed)

        val balanceStatement = balanceConnection.createStatement()
        val balanceResult = balanceStatement.executeQuery("SELECT 1")
        assertTrue(balanceResult.next())
        assertEquals(1, balanceResult.getInt(1))

        balanceConnection.close()
        println("Balance database connection successful")
    }

    @Test
    @Order(2)
    @DisplayName("WireMock server is accessible at localhost:8082")
    fun testWireMockServer() {
        println("\n=== Testing WireMock Server ===")

        val wireMockUrl = "http://localhost:8082"
        println("WireMock URL: $wireMockUrl")

        // Test basic connectivity
        try {
            val url = URL("$wireMockUrl/__admin/mappings")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            assertTrue(responseCode == 200 || responseCode == 404,
                "WireMock admin endpoint should be accessible (got $responseCode)")

            println("WireMock server is accessible")
        } catch (e: Exception) {
            throw AssertionError("WireMock server not accessible at $wireMockUrl: ${e.message}")
        }
    }

    @Test
    @Order(3)
    @DisplayName("Can create tables in databases")
    fun testDatabaseTableCreation() {
        println("\n=== Testing Database Table Creation ===")

        // Create test table in payment database
        val paymentConnection = DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/payment_db",
            "payment_user",
            "payment_password"
        )

        paymentConnection.use { conn ->
            val statement = conn.createStatement()

            // Create test table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS test_table (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(100)
                )
            """)

            // Insert test data
            statement.execute("INSERT INTO test_table (name) VALUES ('test_value')")

            // Query test data
            val result = statement.executeQuery("SELECT COUNT(*) FROM test_table")
            assertTrue(result.next())
            assertEquals(1, result.getInt(1))

            // Clean up
            statement.execute("DROP TABLE test_table")
        }

        println("Database table operations successful")
    }

    @Test
    @Order(4)
    @DisplayName("Expected service ports are accessible")
    fun testServicePorts() {
        println("\n=== Testing Service Port Accessibility ===")

        val services = mapOf(
            "PostgreSQL" to "localhost:5432",
            "Kafka" to "localhost:9092",
            "WireMock" to "localhost:8082"
        )

        services.forEach { (serviceName, endpoint) ->
            val (host, portStr) = endpoint.split(":")
            val port = portStr.toInt()

            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(host, port), 5000)
                socket.close()
                println("✓ $serviceName is accessible at $endpoint")
            } catch (e: Exception) {
                throw AssertionError("✗ $serviceName is NOT accessible at $endpoint: ${e.message}")
            }
        }

        println("All required service ports are accessible")
    }
}