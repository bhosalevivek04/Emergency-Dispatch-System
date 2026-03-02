package com.vivek.dispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DispatchServiceApplication {

	public static void main(String[] args) {
		// DEBUG: Print what Kafka config is being used
		System.out.println("=== KAFKA CONFIG DEBUG ===");
		System.out.println("System Property: " + System.getProperty("spring.kafka.bootstrap-servers"));
		System.out.println("Environment Variable: " + System.getenv("SPRING_KAFKA_BOOTSTRAP_SERVERS"));
		System.out.println("All Kafka System Properties:");
		System.getProperties().stringPropertyNames().stream()
			.filter(key -> key.toLowerCase().contains("kafka"))
			.forEach(key -> System.out.println("  " + key + " = " + System.getProperty(key)));
		System.out.println("========================");
		
		SpringApplication.run(DispatchServiceApplication.class, args);
	}

}
