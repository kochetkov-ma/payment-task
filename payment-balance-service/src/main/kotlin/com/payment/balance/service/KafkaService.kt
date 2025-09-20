package com.payment.balance.service

import com.payment.balance.dto.kafka.HoldResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaService(
    private val kafkaTemplate: KafkaTemplate<String, HoldResponse>
) {

    private val logger = LoggerFactory.getLogger(KafkaService::class.java)

    @Value("\${kafka.topics.payment-hold-response}")
    private lateinit var holdResponseTopic: String

    fun sendHoldResponse(response: HoldResponse) {
        try {
            logger.info("Sending hold response for payment ${response.paymentId}: success=${response.success}")

            kafkaTemplate.send(holdResponseTopic, response.paymentId.toString(), response)
                .whenComplete { _, ex ->
                    if (ex == null) {
                        logger.info("Successfully sent hold response for payment ${response.paymentId} to topic $holdResponseTopic")
                    } else {
                        logger.error("Failed to send hold response for payment ${response.paymentId}", ex)
                    }
                }
        } catch (e: Exception) {
            logger.error("Error sending hold response for payment ${response.paymentId}", e)
        }
    }
}