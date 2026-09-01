package com.internship.Academic.Assistant;

import com.internship.Academic.Assistant.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class EmbeddingServiceTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void testEmbedding() {

        String text =
                "Database Management Systems is part of the syllabus.";

        List<Double> embedding =
                embeddingService.embedDocument(text);

        System.out.println("Embedding size: "
                + embedding.size());

        System.out.println("First 5 values:");

        embedding.stream()
                .limit(5)
                .forEach(System.out::println);
    }
}