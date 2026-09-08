package com.internship.Academic.Assistant.service;

import com.internship.Academic.Assistant.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionAnswerService {

    private static final double MIN_SIMILARITY = 0.45;

    private static final String NOT_FOUND_REPLY =
            "Mai nahi btaunga, kyunki mujhe pta nhi h 😂";

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

        if (question == null || question.isBlank()) {
            return NOT_FOUND_REPLY;
        }

        try {

            List<RetrievalService.RetrievalResult> results =
                    retrievalService.retrieve(question, 5);

            /*
             * No relevant document information found.
             */
            if (results == null || results.isEmpty()) {
                return NOT_FOUND_REPLY;
            }

            /*
             * Check whether the best retrieved chunk
             * is relevant enough to the user's question.
             */
            double bestScore = results.get(0).score();

            System.out.println(
                    "Best retrieval similarity score: " + bestScore
            );

            if (bestScore < MIN_SIMILARITY) {
                System.out.println(
                        "Question considered outside available documents."
                );

                return NOT_FOUND_REPLY;
            }

            /*
             * Build context from the most relevant document chunks.
             */
            StringBuilder context = new StringBuilder();

            for (RetrievalService.RetrievalResult result : results) {

                if (result == null || result.chunk() == null) {
                    continue;
                }

                DocumentChunk chunk = result.chunk();

                if (chunk.getContent() == null
                        || chunk.getContent().isBlank()) {
                    continue;
                }

                context.append(chunk.getContent());
                context.append("\n\n");
            }

            /*
             * If retrieval technically returned results
             * but there is no usable text, treat it as not found.
             */
            if (context.isEmpty()) {
                return NOT_FOUND_REPLY;
            }

            /*
             * Send only the retrieved document context to Groq.
             */
            String answer = groqAnswerService.generateAnswer(
                    question,
                    context.toString()
            );

            /*
             * Safety fallback if Groq fails or returns nothing.
             */
            if (answer == null || answer.isBlank()) {
                return NOT_FOUND_REPLY;
            }

            return answer.trim();

        } catch (Exception e) {

            System.err.println(
                    "Question answering failed: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + e.getMessage()
            );

            return NOT_FOUND_REPLY;
        }
    }
}