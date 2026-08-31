package com.internship.Academic.Assistant;

import com.internship.Academic.Assistant.service.DocumentReaderService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AcademicAssistantApplication {

	public static void main(String[] args) {
		SpringApplication.run(AcademicAssistantApplication.class, args);
	}

	@Bean
	CommandLineRunner testDocuments(DocumentReaderService documentReaderService) {
		return args -> documentReaderService.processDocuments();
	}
}