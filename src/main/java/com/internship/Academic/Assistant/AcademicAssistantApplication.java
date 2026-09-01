package com.internship.Academic.Assistant;

import com.internship.Academic.Assistant.service.DocumentReaderService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AcademicAssistantApplication implements CommandLineRunner {

	private final DocumentReaderService documentReaderService;

	public AcademicAssistantApplication(
			DocumentReaderService documentReaderService
	) {
		this.documentReaderService = documentReaderService;
	}

	public static void main(String[] args) {
		SpringApplication.run(AcademicAssistantApplication.class, args);
	}
	@Override
	public void run(String... args) {
		documentReaderService.processDocuments();
	}
}