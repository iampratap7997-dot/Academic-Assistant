package com.internship.Academic.Assistant.model;

public class DocumentChunk {

    private String documentName;
    private int chunkNumber;
    private String content;

    public DocumentChunk(String documentName, int chunkNumber, String content) {
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

    @Override
    public String toString() {
        return "DocumentChunk{" +
                "documentName='" + documentName + '\'' +
                ", chunkNumber=" + chunkNumber +
                ", contentLength=" + content.length() +
                '}';
    }
}