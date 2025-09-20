FROM openjdk:21-jdk-slim

LABEL org.opencontainers.image.source=https://github.com/kochetkov-ma/payment-task
LABEL org.opencontainers.image.description="Payment Balance Service"
LABEL org.opencontainers.image.licenses=MIT
LABEL org.opencontainers.image.title="Payment Balance Service"
LABEL org.opencontainers.image.vendor="kochetkov-ma"

COPY build/libs/payment-balance-service-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]