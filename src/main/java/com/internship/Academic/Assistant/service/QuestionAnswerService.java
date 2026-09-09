package com.internship.Academic.Assistant.service;

import com.internship.Academic.Assistant.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class QuestionAnswerService {

    /*
     * Minimum similarity required for a question
     * to be considered relevant to our documents.
     */
    private static final double MIN_SIMILARITY = 0.45;

    /*
     * Different natural fallback replies.
     *
     * One of these will be selected randomly whenever
     * the required information cannot be found in the
     * available academic documents.
     */
    private static final List<String> NOT_FOUND_REPLIES = List.of(

            "I don't know this one 😂\n\n" +
                    "I'm still getting started, so I currently have limited information. " +
                    "More academic information will be added soon!",

            "Oops 😅 I don't have this information yet.\n\n" +
                    "I'm still getting started and my knowledge is currently limited " +
                    "to the available academic documents.",

            "Hmm... I couldn't find this in my current academic information 🤔\n\n" +
                    "More information will be added soon!",

            "I'm not sure about this one 😅\n\n" +
                    "I can currently answer only from the academic documents " +
                    "that have been added to me.",

            "Sorry 😂 I don't have enough information about this yet.\n\n" +
                    "I'm still being built, so more academic information will be added soon!",

            "I don't have an answer for this one right now 😅\n\n" +
                    "I'm currently learning from a limited set of academic documents. " +
                    "More information will be added soon!"
    );

    private final RetrievalService retrievalService;
    private final GroqAnswerService groqAnswerService;

    public QuestionAnswerService(
            RetrievalService retrievalService,
            GroqAnswerService groqAnswerService
    ) {
        this.retrievalService = retrievalService;
        this.groqAnswerService = groqAnswerService;
    }

    /*
     * Select one fallback reply randomly.
     */
    private String getRandomNotFoundReply() {

        Random random = new Random();

        int index = random.nextInt(NOT_FOUND_REPLIES.size());

        return NOT_FOUND_REPLIES.get(index);
    }

    public String answer(String question) {

        /*
         * Empty question.
         */
        if (question == null || question.isBlank()) {

            return getRandomNotFoundReply();
        }

        try {

            /*
             * Retrieve the five most relevant chunks
             * from the available documents.
             */
            List<RetrievalService.RetrievalResult> results =
                    retrievalService.retrieve(question, 5);

            /*
             * Nothing relevant was retrieved.
             */
            if (results == null || results.isEmpty()) {

                System.out.println(
                        "No relevant document chunks found."
                );

                return getRandomNotFoundReply();
            }

            /*
             * Check the similarity of the best result.
             */
            double bestScore = results.get(0).score();

            System.out.println(
                    "Best retrieval similarity score: " + bestScore
            );

            /*
             * If the best document chunk is not relevant
             * enough, do NOT ask Groq to answer.
             *
             * This prevents the AI from using outside knowledge.
             */
            if (bestScore < MIN_SIMILARITY) {

                System.out.println(
                        "Question considered outside available documents."
                );

                return getRandomNotFoundReply();
            }

            /*
             * Build context using the retrieved chunks.
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
             * Retrieval returned results, but none contained
             * usable text.
             */
            if (context.isEmpty()) {

                System.out.println(
                        "Retrieved chunks contained no usable text."
                );

                return getRandomNotFoundReply();
            }

            /*
             * Send the question and retrieved document context
             * to Groq.
             */
            String answer = groqAnswerService.generateAnswer(
                    question,
                    context.toString()
            );

            /*
             * If Groq doesn't return an answer,
             * use our random fallback.
             */
            if (answer == null || answer.isBlank()) {

                System.out.println(
                        "Groq returned an empty answer."
                );

                return getRandomNotFoundReply();
            }

            /*
             * Return the generated answer.
             */
            return answer.trim();

        } catch (Exception e) {

            System.err.println(
                    "Question answering failed: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + e.getMessage()
            );

            return getRandomNotFoundReply();
        }
    }
}