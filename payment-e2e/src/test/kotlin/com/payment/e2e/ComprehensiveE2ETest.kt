package com.payment.e2e

import com.payment.e2e.utils.TestDataHelper
import org.junit.jupiter.api.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.sql.DriverManager
import java.util.*

/**
 * Comprehensive E2E test that covers all scenarios from final-test.sh
 * and adds additional validations
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@DisplayName("Comprehensive E2E Payment System Test")
class ComprehensiveE2ETest {

    companion object {
        private const val COMMISSION_RATE = 0.01 // 1%
        private lateinit var testUsername: String
        private lateinit var token: String
        private var userId: String? = null

        @JvmStatic
        @BeforeAll
        fun setupAll() {
            println("===========================================")
            println("🚀 COMPREHENSIVE E2E PAYMENT SYSTEM TEST")
            println("===========================================")
            println("Testing against services at:")
            println("  - payment-backend: localhost:8080")
            println("  - payment-balance-service: localhost:8081")
            println("  - PostgreSQL: localhost:5432")
            println("  - Kafka: localhost:9092")
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            println("\n===========================================")
            println("📊 FINAL TEST SUMMARY")
            println("===========================================")

            if (testUsername.isNotBlank()) {
                printFinalReport()
            }

            println("\nTest completed at: ${Date()}")
            println("===========================================")
        }

        private fun printFinalReport() {
            try {
                // Connect to payment database
                val paymentConn = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/payment_db",
                    "payment_user",
                    "payment_password"
                )

                // Get user ID
                val userStmt = paymentConn.prepareStatement(
                    "SELECT id FROM users WHERE username = ?"
                )
                userStmt.setString(1, testUsername)
                val userRs = userStmt.executeQuery()

                if (userRs.next()) {
                    val userId = userRs.getString("id")

                    // Count payments
                    val countStmt = paymentConn.prepareStatement("""
                        SELECT
                            COUNT(*) FILTER (WHERE status = 'COMPLETED') as completed,
                            COUNT(*) FILTER (WHERE status = 'FAILED') as failed,
                            COUNT(*) FILTER (WHERE status = 'PROCESSING') as processing,
                            COUNT(*) as total
                        FROM payments
                        WHERE user_id = ?::uuid
                    """)
                    countStmt.setString(1, userId)
                    val countRs = countStmt.executeQuery()

                    if (countRs.next()) {
                        println("📈 Payment Statistics for user: $testUsername")
                        println("  User ID: $userId")
                        println("  ✅ Completed: ${countRs.getInt("completed")}")
                        println("  ❌ Failed: ${countRs.getInt("failed")}")
                        println("  ⏳ Processing: ${countRs.getInt("processing")}")
                        println("  📊 Total: ${countRs.getInt("total")}")
                    }

                    // List all payments
                    val paymentsStmt = paymentConn.prepareStatement("""
                        SELECT id, amount, status, created_at, callback_url
                        FROM payments
                        WHERE user_id = ?::uuid
                        ORDER BY created_at DESC
                    """)
                    paymentsStmt.setString(1, userId)
                    val paymentsRs = paymentsStmt.executeQuery()

                    println("\n📋 All Payments:")
                    println("%-40s %-10s %-12s %-25s".format("ID", "Amount", "Status", "Created"))
                    println("-".repeat(90))

                    while (paymentsRs.next()) {
                        println("%-40s %-10s %-12s %-25s".format(
                            paymentsRs.getString("id"),
                            paymentsRs.getBigDecimal("amount"),
                            paymentsRs.getString("status"),
                            paymentsRs.getTimestamp("created_at")
                        ))
                    }
                }

                // Connect to balance database
                val balanceConn = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/balance_db",
                    "balance_user",
                    "balance_password"
                )

                // Get final balance
                val balanceStmt = balanceConn.prepareStatement(
                    "SELECT balance, reserved_amount FROM balances WHERE username = ?"
                )
                balanceStmt.setString(1, testUsername)
                val balanceRs = balanceStmt.executeQuery()

                if (balanceRs.next()) {
                    println("\n💰 Final Balance:")
                    println("  Balance: ${balanceRs.getBigDecimal("balance")}")
                    println("  Reserved: ${balanceRs.getBigDecimal("reserved_amount")}")
                }

                // Get transactions
                val transStmt = balanceConn.prepareStatement("""
                    SELECT t.type, t.amount, t.status, t.created_at
                    FROM transactions t
                    JOIN balances b ON t.balance_id = b.id
                    WHERE b.username = ?
                    ORDER BY t.created_at DESC
                    LIMIT 10
                """)
                transStmt.setString(1, testUsername)
                val transRs = transStmt.executeQuery()

                println("\n📝 Recent Transactions:")
                println("%-10s %-10s %-12s %-25s".format("Type", "Amount", "Status", "Created"))
                println("-".repeat(60))

                while (transRs.next()) {
                    println("%-10s %-10s %-12s %-25s".format(
                        transRs.getString("type"),
                        transRs.getBigDecimal("amount"),
                        transRs.getString("status"),
                        transRs.getTimestamp("created_at")
                    ))
                }

                paymentConn.close()
                balanceConn.close()

            } catch (e: Exception) {
                println("⚠️ Could not generate final report: ${e.message}")
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("1️⃣ Complete Payment Flow with All Validations")
    fun testCompletePaymentFlow() {
        println("\n=== TEST 1: Complete Payment Flow ===")

        // Generate unique username (max 50 chars, using only last 8 chars of UUID)
        val uniqueId = UUID.randomUUID().toString().substring(0, 8)
        testUsername = "comp_test_$uniqueId"
        val password = "test123"
        val email = "$testUsername@test.com"

        // Step 1: Register user
        println("\n1. Registering user: $testUsername")
        val registerResponse = TestDataHelper.registerUser(testUsername, password, email)
        if (registerResponse.statusCode() != 201) {
            println("Registration failed with status: ${registerResponse.statusCode()}")
            println("Response body: ${registerResponse.body.asString()}")
        }
        Assertions.assertEquals(201, registerResponse.statusCode(), "User registration should return 201")

        val regBody = registerResponse.jsonPath()
        Assertions.assertNotNull(regBody.getString("token"), "Registration should return token")
        Assertions.assertNotNull(regBody.getString("userId"), "Registration should return userId")
        Assertions.assertEquals(testUsername, regBody.getString("username"), "Username should match")

        userId = regBody.getString("userId")
        println("✅ User registered with ID: $userId")

        // Step 2: Login and get token
        println("\n2. Logging in user")
        token = TestDataHelper.loginUser(testUsername, password)
        Assertions.assertNotNull(token, "Login should return token")
        Assertions.assertFalse(token.isEmpty(), "Token should not be empty")
        println("✅ Token obtained: ${token.substring(0, 20)}...")

        // Step 3: Create initial balance
        println("\n3. Creating initial balance: 1000.00")
        val initialBalance = BigDecimal("1000.00")
        val balanceId = TestDataHelper.createBalanceDirectly(testUsername, initialBalance)
        Assertions.assertNotNull(balanceId, "Balance should be created")

        // Verify balance
        val createdBalance = TestDataHelper.getBalanceDirectly(testUsername)
        Assertions.assertEquals(initialBalance, createdBalance, "Initial balance should be 1000.00")
        println("✅ Balance created and verified: $createdBalance")

        // Step 4: Create successful payment
        println("\n4. Creating payment for 100.00")
        val paymentAmount = BigDecimal("100.00")
        val callbackUrl = "http://localhost:8082/callback"

        val paymentResponse = TestDataHelper.createPayment(token, paymentAmount, callbackUrl)
        Assertions.assertEquals(201, paymentResponse.statusCode(), "Payment creation should return 201")

        val paymentId = paymentResponse.jsonPath().getString("id")
        val initialStatus = paymentResponse.jsonPath().getString("status")

        Assertions.assertNotNull(paymentId, "Payment should have ID")
        Assertions.assertEquals("CREATED", initialStatus, "Initial status should be CREATED")
        println("✅ Payment created with ID: $paymentId")

        // Step 5: Wait for processing
        println("\n5. Waiting for Kafka processing...")
        val processed = TestDataHelper.waitForPaymentStatus(
            token, paymentId, "COMPLETED", 30, 1000
        )
        Assertions.assertTrue(processed, "Payment should be processed to COMPLETED")

        // Step 6: Verify final status
        val finalStatus = TestDataHelper.getPaymentStatus(token, paymentId)
        Assertions.assertEquals(200, finalStatus.statusCode())
        Assertions.assertEquals("COMPLETED", finalStatus.jsonPath().getString("status"))
        println("✅ Payment status: COMPLETED")

        // Step 7: Verify balance deduction with commission
        println("\n6. Verifying balance deduction")
        Thread.sleep(2000) // Allow async operations to complete

        val finalBalance = TestDataHelper.getBalanceDirectly(testUsername)
        val expectedCommission = paymentAmount.multiply(BigDecimal("0.01"))
        val expectedFinalBalance = initialBalance.subtract(paymentAmount.add(expectedCommission))

        Assertions.assertEquals(
            expectedFinalBalance.setScale(2, java.math.RoundingMode.HALF_UP),
            finalBalance?.setScale(2, java.math.RoundingMode.HALF_UP),
            "Balance should be 899.00 (1000 - 100 - 1)"
        )

        println("✅ Balance correctly updated:")
        println("   Initial: $initialBalance")
        println("   Payment: $paymentAmount")
        println("   Commission (1%): $expectedCommission")
        println("   Final: $finalBalance")

        // Step 8: Verify transaction records
        println("\n7. Verifying transaction records")
        val transactionCount = TestDataHelper.getTransactionCount(testUsername)
        Assertions.assertTrue(transactionCount > 0, "Should have at least one transaction")
        println("✅ Transaction count: $transactionCount")

        // Detailed transaction check
        verifyTransactionDetails(testUsername, paymentAmount, expectedCommission)
    }

    @Test
    @Order(2)
    @DisplayName("2️⃣ Insufficient Balance Handling")
    fun testInsufficientBalance() {
        println("\n=== TEST 2: Insufficient Balance ===")

        // Use the same user from previous test
        Assertions.assertNotNull(token, "Should have token from previous test")

        // Attempt payment exceeding balance
        val largeAmount = BigDecimal("5000.00")
        println("\n1. Attempting payment of $largeAmount (exceeds balance)")

        val paymentResponse = TestDataHelper.createPayment(
            token, largeAmount, "http://localhost:8082/callback"
        )
        Assertions.assertEquals(201, paymentResponse.statusCode(), "Payment creation should succeed initially")

        val paymentId = paymentResponse.jsonPath().getString("id")
        println("Payment created with ID: $paymentId")

        // Wait for processing
        println("\n2. Waiting for payment to fail...")
        val failed = TestDataHelper.waitForPaymentStatus(
            token, paymentId, "FAILED", 30, 1000
        )
        Assertions.assertTrue(failed, "Payment should be marked as FAILED")

        // Verify balance unchanged
        println("\n3. Verifying balance unchanged")
        val currentBalance = TestDataHelper.getBalanceDirectly(testUsername)

        // Should still be 899.00 from previous test
        val expectedBalance = BigDecimal("899.00")
        Assertions.assertEquals(
            expectedBalance.setScale(2, java.math.RoundingMode.HALF_UP),
            currentBalance?.setScale(2, java.math.RoundingMode.HALF_UP),
            "Balance should remain unchanged at 899.00"
        )

        println("✅ Balance correctly unchanged: $currentBalance")
        println("✅ Insufficient balance correctly rejected")
    }

    @Test
    @Order(3)
    @DisplayName("3️⃣ Authorization and Validation Tests")
    fun testAuthorizationAndValidation() {
        println("\n=== TEST 3: Authorization and Validation ===")

        // Test missing authorization
        println("\n1. Testing missing authorization")
        val noAuthResponse = TestDataHelper.createPaymentWithoutAuth(
            BigDecimal("100.00"), "http://example.com"
        )
        Assertions.assertTrue(
            noAuthResponse.statusCode() == 401 || noAuthResponse.statusCode() == 403,
            "Should reject without auth (got ${noAuthResponse.statusCode()})"
        )
        println("✅ Correctly rejected with ${noAuthResponse.statusCode()}")

        // Test invalid token
        println("\n2. Testing invalid token")
        val invalidTokenResponse = TestDataHelper.createPaymentWithInvalidToken(
            BigDecimal("100.00"), "http://example.com"
        )
        Assertions.assertTrue(
            invalidTokenResponse.statusCode() == 401 || invalidTokenResponse.statusCode() == 403,
            "Should reject invalid token (got ${invalidTokenResponse.statusCode()})"
        )
        println("✅ Correctly rejected with ${invalidTokenResponse.statusCode()}")

        // Test validation errors (need valid token)
        Assertions.assertNotNull(token, "Should have token from previous test")

        println("\n3. Testing negative amount")
        val negativeResponse = TestDataHelper.createPayment(
            token, BigDecimal("-10.00"), "http://example.com"
        )
        Assertions.assertTrue(
            negativeResponse.statusCode() == 400 || negativeResponse.statusCode() == 403,
            "Should reject negative amount (got ${negativeResponse.statusCode()})"
        )
        println("✅ Correctly rejected with ${negativeResponse.statusCode()}")

        println("\n4. Testing zero amount")
        val zeroResponse = TestDataHelper.createPayment(
            token, BigDecimal("0.00"), "http://example.com"
        )
        Assertions.assertTrue(
            zeroResponse.statusCode() == 400 || zeroResponse.statusCode() == 403,
            "Should reject zero amount (got ${zeroResponse.statusCode()})"
        )
        println("✅ Correctly rejected with ${zeroResponse.statusCode()}")
    }

    private fun verifyTransactionDetails(username: String, amount: BigDecimal, commission: BigDecimal) {
        try {
            val conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/balance_db",
                "balance_user",
                "balance_password"
            )

            val stmt = conn.prepareStatement("""
                SELECT t.type, t.amount, t.status
                FROM transactions t
                JOIN balances b ON t.balance_id = b.id
                WHERE b.username = ?
                ORDER BY t.created_at DESC
                LIMIT 1
            """)
            stmt.setString(1, username)
            val rs = stmt.executeQuery()

            if (rs.next()) {
                val type = rs.getString("type")
                val transAmount = rs.getBigDecimal("amount")
                val status = rs.getString("status")

                println("✅ Latest transaction:")
                println("   Type: $type")
                println("   Amount: $transAmount")
                println("   Status: $status")

                Assertions.assertEquals("HOLD", type, "Transaction type should be HOLD")
                Assertions.assertEquals("COMPLETED", status, "Transaction status should be COMPLETED")

                // Amount should be payment amount (commission is calculated separately)
                Assertions.assertEquals(
                    amount.setScale(2, java.math.RoundingMode.HALF_UP),
                    transAmount.setScale(2, java.math.RoundingMode.HALF_UP),
                    "Transaction amount should match payment"
                )
            } else {
                Assertions.fail<Unit>("No transactions found for user")
            }

            conn.close()
        } catch (e: Exception) {
            println("⚠️ Could not verify transaction details: ${e.message}")
        }
    }
}