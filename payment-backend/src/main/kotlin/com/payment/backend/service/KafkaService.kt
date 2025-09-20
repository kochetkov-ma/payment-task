package com.payment.backend.service

import com.payment.backend.dto.kafka.HoldRequest
import com.payment.backend.dto.kafka.HoldResponse
import com.payment.backend.entity.PaymentEntity
import com.payment.backend.entity.PaymentStatus
import com.payment.backend.repository.PaymentRepository
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaService(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val paymentRepository: PaymentRepository,
    private val callbackService: CallbackService
) {

    companion object {
        const val PAYMENT_HOLD_REQUEST_TOPIC = "payment-hold-request"
        const val PAYMENT_HOLD_RESPONSE_TOPIC = "payment-hold-response"
    }

    fun sendHoldRequest(payment: PaymentEntity) {
        println("Sending hold request for payment: ${payment.id}, user: ${payment.user.username}")

        val paymentId = payment.id
            ?: throw IllegalStateException("Payment must be saved first - ID is null")

        val holdRequest = HoldRequest(
            paymentId = paymentId,
            userId = payment.user.username, // Fixed: sending actual username instead of user ID
            amount = payment.amount
        )

        kafkaTemplate.send(PAYMENT_HOLD_REQUEST_TOPIC, holdRequest)
            .whenComplete { _, ex ->
                val status = if (ex == null) PaymentStatus.PROCESSING else PaymentStatus.FAILED
                updatePaymentStatus(payment.id, status)
            }
    }

    @KafkaListener(topics = [PAYMENT_HOLD_RESPONSE_TOPIC])
    fun handleHoldResponse(holdResponse: HoldResponse) {
        val status = if (holdResponse.success) {
            PaymentStatus.COMPLETED
        } else {
            PaymentStatus.FAILED
        }

        updatePaymentStatus(holdResponse.paymentId, status)
    }

    private fun updatePaymentStatus(paymentId: java.util.UUID, status: PaymentStatus) {
        val payment = paymentRepository.findById(paymentId).orElse(null)
        if (payment != null) {
            // Update status and save
            payment.status = status
            payment.updatedAt = java.time.LocalDateTime.now()
            val updatedPayment = paymentRepository.save(payment)

            // Send callback if payment is completed or failed
            if (status == PaymentStatus.COMPLETED || status == PaymentStatus.FAILED) {
                callbackService.sendCallback(updatedPayment)
            }
        }
    }
}