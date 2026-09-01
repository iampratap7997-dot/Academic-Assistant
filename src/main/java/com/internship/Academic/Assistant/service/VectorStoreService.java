package com.internship.Academic.Assistant.service;

import com.internship.Academic.Assistant.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class VectorStoreService {

    public record VectorEntry(
            DocumentChunk chunk,
            List<Double> embedding
    ) {}

    private final List<VectorEntry> vectors = new ArrayList<>();

    public void add(
            DocumentChunk chunk,
            List<Double> embedding
    ) {
        vectors.add(
                new VectorEntry(chunk, embedding)
        );
    }

    public List<VectorEntry> getAll() {
        return List.copyOf(vectors);
    }

    public int size() {
        return vectors.size();
    }

    public void clear() {
        vectors.clear();
    }

    public List<VectorEntry> search(
            List<Double> queryEmbedding,
            int topK
    ) {

        return vectors.stream()
                .map(entry -> new ScoredEntry(
                        entry,
                        cosineSimilarity(
                                queryEmbedding,
                                entry.embedding()
                        )
                ))
                .sorted(
                        Comparator.comparingDouble(
                                ScoredEntry::score
                        ).reversed()
                )
                .limit(topK)
                .map(ScoredEntry::entry)
                .toList();
    }

    private record ScoredEntry(
            VectorEntry entry,
            double score
    ) {}

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
                (Math.sqrt(magnitudeA) *
                        Math.sqrt(magnitudeB));
    }
}