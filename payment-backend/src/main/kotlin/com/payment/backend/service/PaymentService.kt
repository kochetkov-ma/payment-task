package com.payment.backend.service

import com.payment.backend.dto.payment.PaymentRequest
import com.payment.backend.dto.payment.PaymentResponse
import com.payment.backend.entity.PaymentEntity
import com.payment.backend.entity.PaymentStatus
import com.payment.backend.entity.UserEntity
import com.payment.backend.repository.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val kafkaService: KafkaService
) {

    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    fun createPayment(request: PaymentRequest, user: UserEntity): PaymentResponse {
        // Validate user has an ID
        requireNotNull(user.id) { "User must have an ID to create payment" }

        println("Creating payment for user: ${user.id}, ${user.username}")
        val now = LocalDateTime.now()
        val payment = PaymentEntity(
            id = null, // Will be auto-generated
            amount = request.amount,
            status = PaymentStatus.CREATED,
            callbackUrl = request.callbackUrl,
            user = user,
            externalId = null,
            createdAt = now,
            updatedAt = now,
            createdBy = user.username,
            updatedBy = user.username
        )

        println("Before saving - createdAt: ${payment.createdAt}, updatedAt: ${payment.updatedAt}")

        val savedPayment = paymentRepository.save(payment)
        println("Saved payment: ${savedPayment.id}, user: ${savedPayment.user?.id}, ${savedPayment.user?.username}")

        // Validate the saved payment has an ID before sending to Kafka
        requireNotNull(savedPayment.id) { "Saved payment must have an ID" }
        requireNotNull(savedPayment.user.id) { "Saved payment must have a user with ID" }

        // Send hold request to balance service via Kafka
        kafkaService.sendHoldRequest(savedPayment)

        return toPaymentResponse(savedPayment)
    }

    @Transactional(readOnly = true)
    fun getPaymentById(paymentId: UUID): PaymentResponse? {
        val payment = paymentRepository.findById(paymentId).orElse(null)
        return payment?.let { toPaymentResponse(it) }
    }

    private fun toPaymentResponse(payment: PaymentEntity): PaymentResponse {
        println("Converting payment to response - ID: ${payment.id}, Status: ${payment.status}, User: ${payment.user}, User ID: ${payment.user?.id}")

        val paymentId = payment.id
            ?: throw IllegalStateException("Payment ID cannot be null when creating response")

        val userId = payment.user.id
            ?: throw IllegalStateException("User ID cannot be null when creating payment response")

        return PaymentResponse(
            id = paymentId,
            amount = payment.amount,
            status = payment.status,
            callbackUrl = payment.callbackUrl,
            userId = userId,
            createdAt = payment.createdAt ?: LocalDateTime.now(),
            updatedAt = payment.updatedAt
        )
    }

    @Scheduled(fixedRate = 5000) // Run every 5 seconds
    @Transactional
    fun processCreatedPayments() {
        try {
            val createdPayments = paymentRepository.findByStatus(PaymentStatus.CREATED)

            if (createdPayments.isNotEmpty()) {
                logger.info("Found ${createdPayments.size} payments in CREATED status to process")

                for (payment in createdPayments) {
                    try {
                        logger.info("Processing payment ${payment.id} with amount ${payment.amount}")

                        // Send hold request to Kafka to initiate processing
                        kafkaService.sendHoldRequest(payment)

                        logger.info("Successfully sent hold request for payment ${payment.id}")
                    } catch (e: Exception) {
                        logger.error("Failed to process payment ${payment.id}", e)
                        // Update payment status to FAILED
                        payment.status = PaymentStatus.FAILED
                        payment.updatedAt = LocalDateTime.now()
                        paymentRepository.save(payment)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error in scheduled payment processing", e)
        }
    }
}