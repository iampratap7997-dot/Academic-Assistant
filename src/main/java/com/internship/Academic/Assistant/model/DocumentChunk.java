package com.internship.Academic.Assistant.model;

public class DocumentChunk {

    private final String documentName;
    private final int chunkNumber;
    private final String content;

    public DocumentChunk(
            String documentName,
            int chunkNumber,
            String content
    ) {
        this.documentName = documentName;
        this.chunkNumber = chunkNumber;
        this.content = content;
    }

    public String getDocumentName() {
        return documentName;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public String getContent() {
        return content;
    }
}