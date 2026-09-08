package com.internship.Academic.Assistant.service;

import com.internship.Academic.Assistant.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionAnswerService {

    private final RetrievalService retrievalService;
    private final GeminiAnswerService geminiAnswerService;

    /*
     * Minimum similarity required before we ask Gemini
     * to answer the question.
     *
     * If the question is below this value, we consider it
     * unrelated to the available academic documents.
     */
    private static final double MIN_SIMILARITY = 0.45;

    public QuestionAnswerService(
            RetrievalService retrievalService,
            GeminiAnswerService geminiAnswerService
    ) {
        this.retrievalService = retrievalService;
        this.geminiAnswerService = geminiAnswerService;
    }

    public String answer(String question) {

        /*
         * Retrieve the most relevant document chunks.
         */
        List<RetrievalService.RetrievalResult> results =
                retrievalService.retrieve(question, 5);

        /*
         * Nothing was retrieved.
         */
        if (results.isEmpty()) {

            return "😂 Pata chale to mujhe bhi batana!";
        }

        /*
         * Check how relevant the best matching chunk is.
         */
        double bestScore =
                results.get(0).score();

        System.out.println(
                "Best retrieval similarity score: "
                        + bestScore
        );

        /*
         * If even the best document chunk is not
         * sufficiently related to the question,
         * DO NOT call Gemini.
         *
         * This saves Gemini requests and prevents
         * unrelated questions from consuming quota.
         */
        if (bestScore < MIN_SIMILARITY) {

            System.out.println(
                    "Question appears to be outside the available documents."
            );

            return "😂 Pata chale to mujhe bhi batana!";
        }

        /*
         * Build context using relevant chunks only.
         */
        StringBuilder context =
                new StringBuilder();

        for (RetrievalService.RetrievalResult result
                : results) {

            /*
             * Only include chunks that are reasonably
             * relevant to the question.
             */
            if (result.score() >= MIN_SIMILARITY) {

                DocumentChunk chunk =
                        result.chunk();

                context.append(
                        chunk.getContent()
                );

                context.append("\n\n");
            }
        }

        /*
         * Safety check.
         */
        if (context.isEmpty()) {

            return "😂 Pata chale to mujhe bhi batana!";
        }

        /*
         * Only now call Gemini.
         */
        return geminiAnswerService.generateAnswer(
                question,
                context.toString()
        );
    }
}