package com.internship.Academic.Assistant.service;

import com.internship.Academic.Assistant.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionAnswerService {

    private final RetrievalService retrievalService;
    private final GroqAnswerService groqAnswerService;

    public QuestionAnswerService(
            RetrievalService retrievalService,
            GroqAnswerService groqAnswerService
    ) {
        this.retrievalService = retrievalService;
        this.groqAnswerService = groqAnswerService;
    }

    public String answer(String question) {

        List<RetrievalService.RetrievalResult> results =
                retrievalService.retrieve(question, 5);

        if (results.isEmpty()) {
            return "I couldn't find this information in the available documents.";
        }

        double bestScore = results.get(0).score();

        double MIN_SIMILARITY = 0.45;

        if (bestScore < MIN_SIMILARITY) {
            return "I couldn't find this information in the available documents.";
        }

        StringBuilder context = new StringBuilder();

        for (RetrievalService.RetrievalResult result : results) {

            DocumentChunk chunk = result.chunk();

            context.append(chunk.getContent());
            context.append("\n\n");
        }

        return groqAnswerService.generateAnswer(
                question,
                context.toString()
        );
    }
}