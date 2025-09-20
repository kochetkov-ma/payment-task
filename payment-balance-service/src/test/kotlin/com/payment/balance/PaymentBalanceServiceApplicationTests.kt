package com.payment.balance

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(properties = [
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
])
@ActiveProfiles("test")
class PaymentBalanceServiceApplicationTests {

    @Test
    fun contextLoads() {
    }
}