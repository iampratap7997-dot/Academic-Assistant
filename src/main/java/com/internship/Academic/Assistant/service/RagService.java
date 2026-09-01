package com.internship.Academic.Assistant.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private final EmbeddingService embeddingService;
    private final SimilaritySearchService similaritySearchService;

    public RagService(
            EmbeddingService embeddingService,
            SimilaritySearchService similaritySearchService
    ) {
        this.embeddingService = embeddingService;
        this.similaritySearchService = similaritySearchService;
    }

    public List<VectorStoreService.VectorEntry> retrieve(
            String question
    ) {

        List<Double> queryEmbedding =
                embeddingService.embedQuery(question);

        return similaritySearchService.search(
                queryEmbedding,
                5
        );
    }
}