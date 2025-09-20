package com.payment.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PaymentBackendApplication

fun main(args: Array<String>) {
    runApplication<PaymentBackendApplication>(*args)
}