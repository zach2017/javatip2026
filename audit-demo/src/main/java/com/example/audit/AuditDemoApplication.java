package com.example.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AuditDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditDemoApplication.class, args);
    }
}
