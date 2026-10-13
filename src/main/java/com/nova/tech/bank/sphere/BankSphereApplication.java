package com.nova.tech.bank.sphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BankSphereApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankSphereApplication.class, args);
	}

}
