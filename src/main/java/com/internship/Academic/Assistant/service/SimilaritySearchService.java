package com.internship.Academic.Assistant.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SimilaritySearchService {

    private final VectorStoreService vectorStoreService;

    public SimilaritySearchService(
            VectorStoreService vectorStoreService
    ) {
        this.vectorStoreService = vectorStoreService;
    }

    public List<VectorStoreService.VectorEntry> search(
            List<Double> queryEmbedding,
            int topK
    ) {

        List<VectorStoreService.VectorEntry> allVectors =
                vectorStoreService.getAll();

        List<ScoredVector> scoredVectors = new ArrayList<>();

        for (VectorStoreService.VectorEntry entry : allVectors) {

            double similarity = cosineSimilarity(
                    queryEmbedding,
                    entry.embedding()
            );

            scoredVectors.add(
                    new ScoredVector(
                            entry,
                            similarity
                    )
            );
        }

        scoredVectors.sort(
                Comparator.comparingDouble(
                        ScoredVector::score
                ).reversed()
        );

        return scoredVectors.stream()
                .limit(topK)
                .map(ScoredVector::entry)
                .toList();
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
                (Math.sqrt(magnitudeA) * Math.sqrt(magnitudeB));
    }

    private record ScoredVector(
            VectorStoreService.VectorEntry entry,
            double score
    ) {}
}