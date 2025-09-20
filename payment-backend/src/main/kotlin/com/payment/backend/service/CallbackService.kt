package com.payment.backend.service

import com.payment.backend.entity.PaymentEntity
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.EnableRetry
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
@EnableRetry
class CallbackService(
    private val restTemplate: RestTemplate
) {

    private val logger = LoggerFactory.getLogger(CallbackService::class.java)

    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    fun sendCallback(payment: PaymentEntity) {
        try {
            logger.info("Sending callback for payment ${payment.id} to ${payment.callbackUrl}")

            val callbackData = mapOf(
                "id" to payment.id.toString(),
                "status" to payment.status.name,
                "amount" to payment.amount.toString(),
                "userId" to payment.user.id.toString()
            )

            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }

            val request = HttpEntity(callbackData, headers)

            val response = restTemplate.postForEntity(payment.callbackUrl, request, String::class.java)

            logger.info("Successfully sent callback for payment ${payment.id}. Response status: ${response.statusCode}")
        } catch (ex: Exception) {
            logger.error("Failed to send callback for payment ${payment.id} to ${payment.callbackUrl} after all retries", ex)
            // Don't throw exception - just log the error so payment processing can continue
            // The payment status has already been updated, we don't want to fail the entire transaction
            // just because the callback failed
        }
    }
}