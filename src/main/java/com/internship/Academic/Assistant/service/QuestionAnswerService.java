package com.internship.Academic.Assistant.service;

import com.internship.Academic.Assistant.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionAnswerService {

    private final RetrievalService retrievalService;
    private final GeminiAnswerService geminiAnswerService;

    public QuestionAnswerService(
            RetrievalService retrievalService,
            GeminiAnswerService geminiAnswerService
    ) {
        this.retrievalService = retrievalService;
        this.geminiAnswerService = geminiAnswerService;
    }

    public String answer(String question) {

        // Retrieve the 5 most relevant document chunks
        List<DocumentChunk> chunks =
                retrievalService.retrieve(question, 5);

        // No relevant information found
        if (chunks.isEmpty()) {
            return "I couldn't find relevant information in the available documents.";
        }

        // Combine retrieved chunks into one context
        StringBuilder context = new StringBuilder();

        for (DocumentChunk chunk : chunks) {
            context.append(chunk.getContent());
            context.append("\n\n");
        }

        // Send question + retrieved context to Gemini
        return geminiAnswerService.generateAnswer(
                question,
                context.toString()
        );
    }
}