package com.solace.samples.queuingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Queuing Service application.
 * This Spring Boot application provides a simple in-memory message queuing system.
 */
@SpringBootApplication
public class QueuingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueuingServiceApplication.class, args);
    }

}
