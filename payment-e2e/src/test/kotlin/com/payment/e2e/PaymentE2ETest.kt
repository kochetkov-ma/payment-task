package com.payment.e2e

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.payment.e2e.utils.TestDataHelper
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class PaymentE2ETest {

    companion object {
        private const val COMMISSION_RATE = 0.01 // 1%

        // Use WireMock client to connect to existing Docker WireMock
        // Note: We don't start our own WireMock server, we use the Docker one

        @JvmStatic
        @BeforeAll
        fun setupAll() {
            println("=== Starting E2E Test Infrastructure ===")
            println("Note: Make sure to run 'docker compose up -d' before running tests")
            println("Expected services:")
            println("  - PostgreSQL: localhost:5432")
            println("  - Kafka: localhost:9092")
            println("  - WireMock: localhost:8082")
            println("  - payment-backend: localhost:8080")
            println("  - payment-balance-service: localhost:8081")

            // Configure WireMock client to use Docker WireMock
            configureFor("localhost", 8082)
            println("Connected to WireMock on port 8082")

            // Configure RestAssured base URI
            RestAssured.baseURI = "http://localhost"
            RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()

            // Services are already running in Docker, don't start them
            // ServiceStarter.startPaymentBackend()
            // ServiceStarter.startBalanceService()

            println("=== E2E Test Infrastructure Ready ===")
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            println("=== Stopping E2E Test Infrastructure ===")
            println("Services remain running in Docker")
            println("=== E2E Test Infrastructure Stopped ===")
        }
    }

    @BeforeEach
    fun setup() {
        // Clean up test data before each test
        TestDataHelper.cleanupTestData()

        // Reset WireMock stubs using client
        reset()
    }

    @Test
    @Order(1)
    @DisplayName("Positive Flow: Complete payment flow with sufficient balance")
    fun testCompletePaymentFlowWithSufficientBalance() {
        println("\n=== TEST: Positive Flow - Complete Payment with Sufficient Balance ===")

        // Test data
        val username = "testuser_${UUID.randomUUID()}"
        val password = "password123"
        val email = "test@example.com"
        val initialBalance = BigDecimal("1000.00")
        val paymentAmount = BigDecimal("100.00")
        val expectedCommission = paymentAmount.multiply(BigDecimal("0.01")) // Use exact decimal representation
        val totalDeduction = paymentAmount.add(expectedCommission)
        // Use host.docker.internal for macOS Docker Desktop to allow container to reach host
        val callbackUrl = "http://host.docker.internal:8082/webhook/payment"

        println("Test user: $username")
        println("Initial balance: $initialBalance")
        println("Payment amount: $paymentAmount")
        println("Expected commission: $expectedCommission")
        println("Total deduction: $totalDeduction")

        // Step 1: Register user in payment-backend
        println("\n1. Registering user...")
        val registerResponse = TestDataHelper.registerUser(username, password, email)
        assertEquals(201, registerResponse.statusCode(), "User registration should succeed")

        // Step 2: Create balance for user in balance-service (direct DB insert)
        println("\n2. Creating balance for user...")
        val balanceId = TestDataHelper.createBalanceDirectly(username, initialBalance)
        assertNotNull(balanceId, "Balance should be created")

        // Verify balance was created
        val createdBalance = TestDataHelper.getBalanceDirectly(username)
        assertEquals(initialBalance, createdBalance, "Initial balance should match")

        // Step 3: Login and get JWT token
        println("\n3. Logging in user...")
        val token = TestDataHelper.loginUser(username, password)
        assertNotNull(token, "JWT token should be returned")
        assertFalse(token.isEmpty(), "JWT token should not be empty")

        // Step 4: Setup WireMock stub for callback
        println("\n4. Setting up WireMock callback stub...")
        stubFor(
            post(urlEqualTo("/webhook/payment"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status": "received"}""")
                )
        )

        // Step 5: Create payment request
        println("\n5. Creating payment request...")
        val paymentResponse = TestDataHelper.createPayment(token, paymentAmount, callbackUrl)
        assertEquals(201, paymentResponse.statusCode(), "Payment creation should succeed")

        val paymentId = paymentResponse.jsonPath().getString("id")
        val initialStatus = paymentResponse.jsonPath().getString("status")
        assertNotNull(paymentId, "Payment ID should be returned")
        assertEquals("CREATED", initialStatus, "Initial payment status should be CREATED")

        println("Created payment ID: $paymentId")

        // Step 6: Wait for payment processing (async flow)
        println("\n6. Waiting for payment processing...")
        val isProcessed = TestDataHelper.waitForPaymentStatus(
            token = token,
            paymentId = paymentId,
            expectedStatus = "COMPLETED",
            maxRetries = 30,
            delayMillis = 1000
        )

        assertTrue(isProcessed, "Payment should be processed to COMPLETED status")

        // Step 7: Verify final payment status
        println("\n7. Verifying final payment status...")
        val finalPaymentResponse = TestDataHelper.getPaymentStatus(token, paymentId)
        assertEquals(200, finalPaymentResponse.statusCode())
        assertEquals("COMPLETED", finalPaymentResponse.jsonPath().getString("status"))

        // Step 8: Verify balance was reduced by amount + commission
        println("\n8. Verifying balance deduction...")
        Thread.sleep(2000) // Give time for async balance update

        val finalBalance = TestDataHelper.getBalanceDirectly(username)
        assertNotNull(finalBalance, "Final balance should exist")

        val expectedFinalBalance = initialBalance.subtract(totalDeduction)

        println("Balance comparison details:")
        println("  Initial balance: $initialBalance")
        println("  Payment amount: $paymentAmount")
        println("  Commission (1%): $expectedCommission")
        println("  Total deduction: $totalDeduction")
        println("  Expected final: $expectedFinalBalance")
        println("  Actual final: $finalBalance")
        println("  Comparison result: ${expectedFinalBalance.compareTo(finalBalance)}")

        // Use setScale for consistent BigDecimal comparison (2 decimal places)
        val expectedFinalScaled = expectedFinalBalance.setScale(2, java.math.RoundingMode.HALF_UP)
        val actualFinalScaled = finalBalance?.setScale(2, java.math.RoundingMode.HALF_UP)

        assertEquals(
            expectedFinalScaled,
            actualFinalScaled,
            "Balance should be reduced by payment amount + commission. Expected: $expectedFinalScaled, Actual: $actualFinalScaled"
        )

        println("Balance verification: Initial=$initialBalance, Final=$finalBalance, Deduction=$totalDeduction")

        // Step 9: Verify transaction was recorded
        println("\n9. Verifying transaction record...")
        val transactionCount = TestDataHelper.getTransactionCount(username)
        assertTrue(transactionCount > 0, "At least one transaction should be recorded")
        println("Transaction count for user: $transactionCount")

        // Step 10: Verify callback was sent for COMPLETED payment
        println("\n10. Verifying callback for COMPLETED payment...")
        Thread.sleep(3000) // Give time for callback

        val callbackRequests = findAll(
            postRequestedFor(urlEqualTo("/webhook/payment"))
        )

        println("Callback requests received: ${callbackRequests.size}")

        // ASSERT: Callback MUST be sent for all payment statuses
        assertTrue(
            callbackRequests.isNotEmpty(),
            "❌ FAILED: Callback MUST be sent for COMPLETED payment! No callback received."
        )
        println("✅ Callback was sent successfully")

        // Verify callback payload
        val callbackBody = callbackRequests[0].bodyAsString
        println("Callback body: $callbackBody")
        assertNotNull(callbackBody, "Callback should have body")
        assertTrue(callbackBody.contains(paymentId), "Callback should contain payment ID")
        assertTrue(callbackBody.contains("COMPLETED"), "Callback should indicate COMPLETED status")
        println("✅ Callback content verified with COMPLETED status")

        println("\n=== POSITIVE FLOW TEST COMPLETED SUCCESSFULLY ===\n")
    }

    @Test
    @Order(2)
    @DisplayName("Negative Flow: Payment fails with insufficient balance")
    fun testPaymentFailsWithInsufficientBalance() {
        println("\n=== TEST: Negative Flow - Payment Fails with Insufficient Balance ===")

        // Test data
        val username = "pooruser_${UUID.randomUUID()}"
        val password = "password123"
        val email = "poor@example.com"
        val initialBalance = BigDecimal("50.00")
        val paymentAmount = BigDecimal("100.00") // More than available balance
        val expectedCommission = paymentAmount.multiply(BigDecimal("0.01")) // Use exact decimal representation
        val totalRequired = paymentAmount.add(expectedCommission)
        // Use host.docker.internal for macOS Docker Desktop to allow container to reach host
        val callbackUrl = "http://host.docker.internal:8082/webhook/payment"

        println("Test user: $username")
        println("Initial balance: $initialBalance")
        println("Payment amount: $paymentAmount")
        println("Total required (with commission): $totalRequired")

        // Step 1: Register user
        println("\n1. Registering user with limited balance...")
        val registerResponse = TestDataHelper.registerUser(username, password, email)
        assertEquals(201, registerResponse.statusCode(), "User registration should succeed")

        // Step 2: Create limited balance
        println("\n2. Creating limited balance...")
        val balanceId = TestDataHelper.createBalanceDirectly(username, initialBalance)
        assertNotNull(balanceId, "Balance should be created")

        // Step 3: Login
        println("\n3. Logging in user...")
        val token = TestDataHelper.loginUser(username, password)
        assertNotNull(token, "JWT token should be returned")

        // Step 4: Setup WireMock stub for callback
        println("\n4. Setting up WireMock callback stub...")
        stubFor(
            post(urlEqualTo("/webhook/payment"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status": "received"}""")
                )
        )

        // Step 5: Attempt payment exceeding balance
        println("\n5. Attempting payment exceeding available balance...")
        val paymentResponse = TestDataHelper.createPayment(token, paymentAmount, callbackUrl)
        assertEquals(201, paymentResponse.statusCode(), "Payment creation should succeed initially")

        val paymentId = paymentResponse.jsonPath().getString("id")
        assertNotNull(paymentId, "Payment ID should be returned")
        println("Created payment ID: $paymentId")

        // Step 6: Wait for payment processing (should fail)
        println("\n6. Waiting for payment to fail...")
        val isFailed = TestDataHelper.waitForPaymentStatus(
            token = token,
            paymentId = paymentId,
            expectedStatus = "FAILED",
            maxRetries = 30,
            delayMillis = 1000
        )

        assertTrue(isFailed, "Payment should be marked as FAILED")

        // Step 7: Verify payment status is FAILED
        println("\n7. Verifying payment status is FAILED...")
        val finalPaymentResponse = TestDataHelper.getPaymentStatus(token, paymentId)
        assertEquals(200, finalPaymentResponse.statusCode())
        assertEquals("FAILED", finalPaymentResponse.jsonPath().getString("status"))

        // Step 8: Verify balance remained unchanged
        println("\n8. Verifying balance is unchanged...")
        Thread.sleep(2000) // Give time for any async operations

        val finalBalance = TestDataHelper.getBalanceDirectly(username)
        assertNotNull(finalBalance, "Final balance should exist")
        assertEquals(
            0,
            initialBalance.compareTo(finalBalance),
            "Balance should remain unchanged. Expected: $initialBalance, Actual: $finalBalance"
        )

        println("Balance verification: Initial=$initialBalance, Final=$finalBalance (unchanged)")

        // Step 9: Verify callback was sent for FAILED payment
        println("\n9. Verifying callback for FAILED payment...")
        Thread.sleep(3000) // Give time for callback

        val callbackRequests = findAll(
            postRequestedFor(urlEqualTo("/webhook/payment"))
        )

        println("Number of callback requests received: ${callbackRequests.size}")

        // ASSERT: Callback MUST be sent for all payment statuses including FAILED
        assertTrue(
            callbackRequests.isNotEmpty(),
            "❌ FAILED: Callback MUST be sent for FAILED payment! No callback received."
        )
        println("✅ Callback was sent for failed payment")

        // Verify callback content
        val callbackBody = callbackRequests[0].bodyAsString
        println("Callback body: $callbackBody")
        assertNotNull(callbackBody, "Callback should have body")
        assertTrue(callbackBody.contains(paymentId), "Callback should contain payment ID")
        assertTrue(callbackBody.contains("FAILED"), "Callback should indicate FAILED status")
        println("✅ Callback content verified with FAILED status")

        println("\n=== NEGATIVE FLOW TEST COMPLETED SUCCESSFULLY ===\n")
    }

    @Test
    @Order(3)
    @DisplayName("Authorization: Payment creation fails without valid token")
    fun testPaymentRequiresAuthentication() {
        println("\n=== TEST: Authorization - Payment Requires Valid Token ===")

        val paymentAmount = BigDecimal("100.00")
        // Use host.docker.internal for macOS Docker Desktop to allow container to reach host
        val callbackUrl = "http://host.docker.internal:8082/webhook/payment"

        // Attempt to create payment without token
        println("\n1. Attempting payment without authentication token...")
        val response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(
                mapOf(
                    "amount" to paymentAmount,
                    "callbackUrl" to callbackUrl
                )
            )
            .post("http://localhost:8080/api/payments")

        // Spring Security returns 403 for unauthenticated requests
        assertTrue(response.statusCode() == 401 || response.statusCode() == 403,
            "Should return 401 or 403 for missing auth (actual: ${response.statusCode()})")
        println("Correctly rejected with ${response.statusCode()}")

        // Attempt with invalid token
        println("\n2. Attempting payment with invalid token...")
        val invalidResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer invalid_token_12345")
            .body(
                mapOf(
                    "amount" to paymentAmount,
                    "callbackUrl" to callbackUrl
                )
            )
            .post("http://localhost:8080/api/payments")

        assertTrue(invalidResponse.statusCode() == 401 || invalidResponse.statusCode() == 403,
            "Should return 401 or 403 for invalid token (actual: ${invalidResponse.statusCode()})")
        println("Correctly rejected invalid token with ${invalidResponse.statusCode()}")

        println("\n=== AUTHORIZATION TEST COMPLETED SUCCESSFULLY ===\n")
    }

    @Test
    @Order(4)
    @DisplayName("Validation: Payment creation fails with invalid amount")
    fun testPaymentValidation() {
        println("\n=== TEST: Validation - Payment Amount Validation ===")

        // Setup user
        val username = "validuser_${UUID.randomUUID()}"
        val password = "password123"
        val email = "valid@example.com"

        TestDataHelper.registerUser(username, password, email)
        val token = TestDataHelper.loginUser(username, password)

        // Test negative amount
        println("\n1. Testing negative amount...")
        val negativeResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer $token")
            .body(
                mapOf(
                    "amount" to BigDecimal("-10.00"),
                    "callbackUrl" to "http://example.com/callback"
                )
            )
            .post("http://localhost:8080/api/payments")

        assertTrue(negativeResponse.statusCode() == 400 || negativeResponse.statusCode() == 403,
            "Should reject negative amount with 400 or 403 (actual: ${negativeResponse.statusCode()})")
        println("Correctly rejected negative amount with ${negativeResponse.statusCode()}")

        // Test zero amount
        println("\n2. Testing zero amount...")
        val zeroResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer $token")
            .body(
                mapOf(
                    "amount" to BigDecimal("0.00"),
                    "callbackUrl" to "http://example.com/callback"
                )
            )
            .post("http://localhost:8080/api/payments")

        assertTrue(zeroResponse.statusCode() == 400 || zeroResponse.statusCode() == 403,
            "Should reject zero amount with 400 or 403 (actual: ${zeroResponse.statusCode()})")
        println("Correctly rejected zero amount with ${zeroResponse.statusCode()}")

        // Test missing callback URL
        println("\n3. Testing missing callback URL...")
        val missingCallbackResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer $token")
            .body(
                mapOf(
                    "amount" to BigDecimal("100.00")
                    // Missing callbackUrl
                )
            )
            .post("http://localhost:8080/api/payments")

        assertTrue(missingCallbackResponse.statusCode() == 400 || missingCallbackResponse.statusCode() == 403,
            "Should reject missing callback URL with 400 or 403 (actual: ${missingCallbackResponse.statusCode()})")
        println("Correctly rejected missing callback URL with ${missingCallbackResponse.statusCode()}")

        println("\n=== VALIDATION TEST COMPLETED SUCCESSFULLY ===\n")
    }
}