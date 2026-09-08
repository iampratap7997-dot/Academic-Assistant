package com.internship.Academic.Assistant.service;

import com.internship.Academic.Assistant.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionAnswerService {

    private final RetrievalService retrievalService;
    private final GeminiAnswerService geminiAnswerService;

    // Minimum similarity required for a result to be considered relevant.
    private static final double MIN_SIMILARITY = 0.45;

    public QuestionAnswerService(
            RetrievalService retrievalService,
            GeminiAnswerService geminiAnswerService
    ) {
        this.retrievalService = retrievalService;
        this.geminiAnswerService = geminiAnswerService;
    }

    public String answer(String question) {

        // Retrieve the most relevant document chunks
        List<RetrievalService.RetrievalResult> results =
                retrievalService.retrieve(question, 5);

        // No documents available
        if (results.isEmpty()) {
            return "I couldn't find relevant information in the available documents.";
        }

        // Check whether the best result is actually relevant
        double bestScore = results.get(0).score();

        if (bestScore < MIN_SIMILARITY) {
            return "I couldn't find relevant information in the available documents.";
        }

        // Combine only relevant chunks into context
        StringBuilder context = new StringBuilder();

        for (RetrievalService.RetrievalResult result : results) {

            // Ignore weak results
            if (result.score() < MIN_SIMILARITY) {
                continue;
            }

            DocumentChunk chunk = result.chunk();

            context.append(chunk.getContent());
            context.append("\n\n");
        }

        // Safety check in case all results were below threshold
        if (context.isEmpty()) {
            return "I couldn't find relevant information in the available documents.";
        }

        // Send question + relevant context to Gemini
        return geminiAnswerService.generateAnswer(
                question,
                context.toString()
        );
    }
}