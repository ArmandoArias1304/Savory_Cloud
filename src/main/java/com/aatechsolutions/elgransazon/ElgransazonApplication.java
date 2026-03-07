package com.aatechsolutions.elgransazon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ElgransazonApplication {

	public static void main(String[] args) {
		SpringApplication.run(ElgransazonApplication.class, args);
	}

}