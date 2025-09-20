package com.payment.e2e.utils

import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.response.Response
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.*

class TestDataHelper {

    companion object {
        private const val PAYMENT_BACKEND_URL = "http://localhost:8080"
        private const val BALANCE_SERVICE_URL = "http://localhost:8081"

        fun registerUser(username: String, password: String, email: String): Response {
            return RestAssured.given()
                .contentType(ContentType.JSON)
                .body(
                    mapOf(
                        "username" to username,
                        "password" to password,
                        "email" to email
                    )
                )
                .post("$PAYMENT_BACKEND_URL/api/auth/register")
        }

        fun loginUser(username: String, password: String): String {
            val response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(
                    mapOf(
                        "username" to username,
                        "password" to password
                    )
                )
                .post("$PAYMENT_BACKEND_URL/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response()

            return response.jsonPath().getString("token")
        }

        fun createPayment(token: String, amount: BigDecimal, callbackUrl: String): Response {
            return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer $token")
                .body(
                    mapOf(
                        "amount" to amount,
                        "callbackUrl" to callbackUrl
                    )
                )
                .post("$PAYMENT_BACKEND_URL/api/payments")
        }

        fun getPaymentStatus(token: String, paymentId: String): Response {
            return RestAssured.given()
                .header("Authorization", "Bearer $token")
                .get("$PAYMENT_BACKEND_URL/api/payments/$paymentId")
        }

        fun createBalanceDirectly(username: String, balance: BigDecimal): UUID {
            // Direct database insertion for balance since no REST endpoint exists
            val jdbcUrl = "jdbc:postgresql://localhost:5432/balance_db"
            val connection: Connection = DriverManager.getConnection(
                jdbcUrl,
                "balance_user",
                "balance_password"
            )

            connection.use { conn ->
                val balanceId = UUID.randomUUID()
                val statement = conn.prepareStatement(
                    """
                    INSERT INTO balances (id, username, balance, reserved_amount, created_at, updated_at)
                    VALUES (?, ?, ?, 0, NOW(), NOW())
                    """
                )
                statement.setObject(1, balanceId)
                statement.setString(2, username)
                statement.setBigDecimal(3, balance)
                statement.executeUpdate()
                return balanceId
            }
        }

        fun getBalanceDirectly(username: String): BigDecimal? {
            val jdbcUrl = "jdbc:postgresql://localhost:5432/balance_db"
            val connection: Connection = DriverManager.getConnection(
                jdbcUrl,
                "balance_user",
                "balance_password"
            )

            connection.use { conn ->
                val statement = conn.prepareStatement(
                    "SELECT balance FROM balances WHERE username = ?"
                )
                statement.setString(1, username)
                val resultSet = statement.executeQuery()

                return if (resultSet.next()) {
                    resultSet.getBigDecimal("balance")
                } else {
                    null
                }
            }
        }

        fun getTransactionCount(username: String): Int {
            val jdbcUrl = "jdbc:postgresql://localhost:5432/balance_db"
            val connection: Connection = DriverManager.getConnection(
                jdbcUrl,
                "balance_user",
                "balance_password"
            )

            connection.use { conn ->
                val statement = conn.prepareStatement(
                    """
                    SELECT COUNT(*) as count
                    FROM transactions t
                    JOIN balances b ON t.balance_id = b.id
                    WHERE b.username = ?
                    """
                )
                statement.setString(1, username)
                val resultSet = statement.executeQuery()

                return if (resultSet.next()) {
                    resultSet.getInt("count")
                } else {
                    0
                }
            }
        }

        fun cleanupTestData() {
            // Clean up payment database
            val paymentJdbcUrl = "jdbc:postgresql://localhost:5432/payment_db"
            val paymentConnection: Connection = DriverManager.getConnection(
                paymentJdbcUrl,
                "payment_user",
                "payment_password"
            )

            paymentConnection.use { conn ->
                conn.createStatement().execute("TRUNCATE TABLE payments CASCADE")
                conn.createStatement().execute("TRUNCATE TABLE users CASCADE")
            }

            // Clean up balance database
            val balanceJdbcUrl = "jdbc:postgresql://localhost:5432/balance_db"
            val balanceConnection: Connection = DriverManager.getConnection(
                balanceJdbcUrl,
                "balance_user",
                "balance_password"
            )

            balanceConnection.use { conn ->
                conn.createStatement().execute("TRUNCATE TABLE transactions CASCADE")
                conn.createStatement().execute("TRUNCATE TABLE balances CASCADE")
            }
        }

        fun waitForPaymentStatus(
            token: String,
            paymentId: String,
            expectedStatus: String,
            maxRetries: Int = 30,
            delayMillis: Long = 1000
        ): Boolean {
            repeat(maxRetries) {
                val response = getPaymentStatus(token, paymentId)
                if (response.statusCode() == 200) {
                    val status = response.jsonPath().getString("status")
                    if (status == expectedStatus) {
                        return true
                    }
                }
                Thread.sleep(delayMillis)
            }
            return false
        }

        fun createPaymentWithoutAuth(amount: BigDecimal, callbackUrl: String): Response {
            return RestAssured.given()
                .contentType(ContentType.JSON)
                .body(mapOf(
                    "amount" to amount,
                    "callbackUrl" to callbackUrl
                ))
                .post("http://localhost:8080/api/payments")
        }

        fun createPaymentWithInvalidToken(amount: BigDecimal, callbackUrl: String): Response {
            return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer invalid_token_12345")
                .body(mapOf(
                    "amount" to amount,
                    "callbackUrl" to callbackUrl
                ))
                .post("http://localhost:8080/api/payments")
        }
    }
}