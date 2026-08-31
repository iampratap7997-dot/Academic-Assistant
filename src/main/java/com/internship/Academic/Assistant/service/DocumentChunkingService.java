package com.internship.Academic.Assistant.service;

import com.internship.Academic.Assistant.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentChunkingService {

    private static final int CHUNK_SIZE = 1500;
    private static final int OVERLAP = 200;

    public List<DocumentChunk> createChunks(
            String documentName,
            String text
    ) {

        List<DocumentChunk> chunks = new ArrayList<>();

        text = text.replaceAll("\\s+", " ").trim();

        int start = 0;
        int chunkNumber = 1;

        while (start < text.length()) {

            int end = Math.min(
                    start + CHUNK_SIZE,
                    text.length()
            );

            String chunkText = text.substring(start, end);

            chunks.add(
                    new DocumentChunk(
                            documentName,
                            chunkNumber,
                            chunkText
                    )
            );

            chunkNumber++;

            if (end == text.length()) {
                break;
            }

            start = end - OVERLAP;
        }

        return chunks;
    }
}