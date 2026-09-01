package com.internship.Academic.Assistant.repository;

import com.internship.Academic.Assistant.model.DocumentChunk;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class DocumentChunkRepository {

    private final List<DocumentChunk> chunks = new ArrayList<>();

    public void saveAll(List<DocumentChunk> documentChunks) {
        chunks.addAll(documentChunks);
    }

    public List<DocumentChunk> findAll() {
        return Collections.unmodifiableList(chunks);
    }

    public int count() {
        return chunks.size();
    }

    public void clear() {
        chunks.clear();
    }
}