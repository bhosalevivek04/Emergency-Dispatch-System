package com.vivek.ambulance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AmbulanceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AmbulanceServiceApplication.class, args);
	}

}
