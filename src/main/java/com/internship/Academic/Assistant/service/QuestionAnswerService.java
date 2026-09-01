package com.internship.Academic.Assistant.service;

import org.springframework.stereotype.Service;

@Service
public class QuestionAnswerService {

    private final EmbeddingService embeddingService;
    private final SimilarityService similarityService;
    private final VectorStoreService vectorStoreService;
    private final GeminiAnswerService geminiAnswerService;

    public QuestionAnswerService(
            EmbeddingService embeddingService,
            SimilarityService similarityService,
            VectorStoreService vectorStoreService,
            GeminiAnswerService geminiAnswerService
    ) {
        this.embeddingService = embeddingService;
        this.similarityService = similarityService;
        this.vectorStoreService = vectorStoreService;
        this.geminiAnswerService = geminiAnswerService;
    }

    public String answer(String question) {

        // 1. Convert the question into an embedding
        var queryEmbedding =
                embeddingService.embedQuery(question);

        // 2. Find the most similar document chunk
        VectorStoreService.VectorEntry bestEntry = null;
        double bestScore = -1;

        for (var entry : vectorStoreService.getAll()) {

            double score = similarityService.cosineSimilarity(
                    queryEmbedding,
                    entry.embedding()
            );

            if (score > bestScore) {
                bestScore = score;
                bestEntry = entry;
            }
        }

        // 3. No relevant document found
        if (bestEntry == null) {
            return "I couldn't find relevant information.";
        }

        // 4. Send the question + relevant document context to Gemini
        return geminiAnswerService.generateAnswer(
                question,
                bestEntry.chunk().getContent()
        );
    }
}