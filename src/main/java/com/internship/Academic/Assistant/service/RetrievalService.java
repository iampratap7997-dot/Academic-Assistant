package com.internship.Academic.Assistant.service;

import com.internship.Academic.Assistant.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RetrievalService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public RetrievalService(
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService
    ) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    public List<RetrievalResult> retrieve(
            String query,
            int topK
    ) {

        // Generate embedding for user's question
        List<Double> queryEmbedding =
                embeddingService.embedQuery(query);

        // Calculate similarity with every stored document chunk
        List<RetrievalResult> scoredChunks = new ArrayList<>();

        for (VectorStoreService.VectorEntry entry
                : vectorStoreService.getAll()) {

            double similarity =
                    cosineSimilarity(
                            queryEmbedding,
                            entry.embedding()
                    );

            scoredChunks.add(
                    new RetrievalResult(
                            entry.chunk(),
                            similarity
                    )
            );
        }

        // Highest similarity first
        scoredChunks.sort(
                Comparator.comparingDouble(
                        RetrievalResult::score
                ).reversed()
        );

        // Return only top K results
        return scoredChunks.subList(
                0,
                Math.min(topK, scoredChunks.size())
        );
    }

    private double cosineSimilarity(
            List<Double> vectorA,
            List<Double> vectorB
    ) {

        if (vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException(
                    "Embedding dimensions do not match."
            );
        }

        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {

            double a = vectorA.get(i);
            double b = vectorB.get(i);

            dotProduct += a * b;

            magnitudeA += a * a;
            magnitudeB += b * b;
        }

        if (magnitudeA == 0 || magnitudeB == 0) {
            return 0.0;
        }

        return dotProduct /
                (
                        Math.sqrt(magnitudeA)
                                * Math.sqrt(magnitudeB)
                );
    }

    public record RetrievalResult(
            DocumentChunk chunk,
            double score
    ) {
    }
}