package com.internship.Academic.Assistant.model;

public class SearchResult {

    private final DocumentChunk chunk;
    private final double score;

    public SearchResult(
            DocumentChunk chunk,
            double score
    ) {
        this.chunk = chunk;
        this.score = score;
    }

    public DocumentChunk getChunk() {
        return chunk;
    }

    public double getScore() {
        return score;
    }
}