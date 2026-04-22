package com.example.demo.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.io.*;

@Component
public class OrderEvents {

    @KafkaListener(topics = "orders.created", groupId = "demo")
    public void onOrderCreated(String payload) {
        // Unbounded buffer - DoS risk if producer floods
        StringBuilder sb = new StringBuilder();
        sb.append(payload);
    }

    @RabbitListener(queues = "shipments")
    public void onShipment(byte[] msg) throws Exception {
        // Deserializing AMQP payload as Java object
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(msg));
        ois.readObject();
    }

    @MessageMapping("/chat/{room}")
    public void onChat(String body) {
        // WebSocket handler
    }

    @EventListener
    public void onApplicationEvent(Object event) {
        // Internal-only listener
    }

    @Scheduled(fixedDelay = 60000)
    public void syncJob() throws IOException {
        // Scheduled disk read
        Files.readString(Paths.get("/etc/app/sync.cfg"));
    }

    @Async
    public void fireAndForget() {
        // Background work
    }
}
