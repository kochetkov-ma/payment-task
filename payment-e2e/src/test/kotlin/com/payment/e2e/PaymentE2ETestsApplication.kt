package com.payment.e2e

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PaymentE2ETestsApplication

fun main(args: Array<String>) {
    runApplication<PaymentE2ETestsApplication>(*args)
}