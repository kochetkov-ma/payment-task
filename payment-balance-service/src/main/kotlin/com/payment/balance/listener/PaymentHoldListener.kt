package com.payment.balance.listener

import com.payment.balance.dto.kafka.HoldRequest
import com.payment.balance.dto.kafka.HoldResponse
import com.payment.balance.service.BalanceService
import com.payment.balance.service.KafkaService
import com.payment.balance.service.TransactionService
import com.payment.balance.service.UserLookupService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentHoldListener(
    private val balanceService: BalanceService,
    private val transactionService: TransactionService,
    private val kafkaService: KafkaService,
    private val userLookupService: UserLookupService
) {

    private val logger = LoggerFactory.getLogger(PaymentHoldListener::class.java)

    @KafkaListener(topics = ["\${kafka.topics.payment-hold-request}"])
    @Transactional
    fun handleHoldRequest(request: HoldRequest) {
        logger.info("Received hold request for payment ${request.paymentId}, userId=${request.userId}, amount=${request.amount}")

        try {
            // Since userId now contains the actual username, we can use it directly
            val username = request.userId
            logger.info("Processing hold request for username: $username")

            // Attempt to hold the amount
            val holdResult = balanceService.holdAmount(username, request.amount, request.paymentId)

            if (holdResult.success && holdResult.balance != null) {
                // Create transaction record
                transactionService.createHoldTransaction(
                    balance = holdResult.balance,
                    amount = request.amount,
                    paymentId = request.paymentId
                )

                logger.info("Successfully held amount ${request.amount} for payment ${request.paymentId}")
            } else {
                logger.warn("Failed to hold amount for payment ${request.paymentId}: ${holdResult.message}")
            }

            // Send response
            val response = HoldResponse(
                paymentId = request.paymentId,
                success = holdResult.success,
                message = holdResult.message,
                commission = holdResult.commission
            )

            kafkaService.sendHoldResponse(response)

        } catch (e: Exception) {
            logger.error("Error processing hold request for payment ${request.paymentId}", e)

            val errorResponse = HoldResponse(
                paymentId = request.paymentId,
                success = false,
                message = "Internal error: ${e.message}",
                commission = null
            )
            kafkaService.sendHoldResponse(errorResponse)
        }
    }
}