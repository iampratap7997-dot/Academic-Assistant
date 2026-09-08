package com.internship.Academic.Assistant.service;

import com.internship.Academic.Assistant.model.DocumentChunk;
import org.apache.tika.Tika;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class DocumentReaderService {

    private final Tika tika =
            new Tika();

    private final DocumentChunkingService chunkingService;

    private final EmbeddingService embeddingService;

    private final VectorStoreService vectorStoreService;

    public DocumentReaderService(
            DocumentChunkingService chunkingService,
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService
    ) {

        this.chunkingService =
                chunkingService;

        this.embeddingService =
                embeddingService;

        this.vectorStoreService =
                vectorStoreService;
    }

    public void processDocuments() {

        try {

            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();

            Resource[] resources =
                    resolver.getResources(
                            "classpath:/documents/*"
                    );

            System.out.println(
                    "\n========================================"
            );

            System.out.println(
                    "Starting document processing..."
            );

            System.out.println(
                    "Documents found: "
                            + resources.length
            );

            System.out.println(
                    "========================================\n"
            );

            /*
             * Clear old vectors before processing.
             */
            vectorStoreService.clear();

            int totalChunks = 0;

            for (
                    Resource resource :
                    resources
            ) {

                String fileName =
                        resource.getFilename();

                System.out.println(
                        "\nProcessing document: "
                                + fileName
                );

                try (
                        InputStream inputStream =
                                resource.getInputStream()
                ) {

                    /*
                     * Extract document text.
                     */
                    String text =
                            tika.parseToString(
                                    inputStream
                            );

                    if (
                            text == null
                                    || text.isBlank()
                    ) {

                        System.out.println(
                                "WARNING: Document is empty: "
                                        + fileName
                        );

                        continue;
                    }

                    /*
                     * Create chunks.
                     */
                    List<DocumentChunk> chunks =
                            chunkingService.createChunks(
                                    fileName,
                                    text
                            );

                    System.out.println(
                            "Chunks created: "
                                    + chunks.size()
                    );

                    if (chunks.isEmpty()) {

                        continue;
                    }

                    /*
                     * Collect all chunk text first.
                     */
                    List<String> chunkTexts =
                            chunks.stream()
                                    .map(
                                            DocumentChunk::getContent
                                    )
                                    .toList();

                    /*
                     * Generate embeddings in batches.
                     *
                     * This is the major performance improvement.
                     */
                    List<List<Double>> embeddings =
                            embeddingService.embedDocuments(
                                    chunkTexts
                            );

                    /*
                     * Store every chunk with its
                     * corresponding embedding.
                     */
                    for (
                            int i = 0;
                            i < chunks.size();
                            i++
                    ) {

                        DocumentChunk chunk =
                                chunks.get(i);

                        List<Double> embedding =
                                embeddings.get(i);

                        vectorStoreService.add(
                                chunk,
                                embedding
                        );

                        totalChunks++;

                        System.out.println(
                                "Chunk "
                                        + (i + 1)
                                        + "/"
                                        + chunks.size()
                                        + " stored successfully."
                        );
                    }

                    System.out.println(
                            "\n========================================"
                    );

                    System.out.println(
                            "Document completed: "
                                    + fileName
                    );

                    System.out.println(
                            "Chunks: "
                                    + chunks.size()
                    );

                    System.out.println(
                            "========================================"
                    );
                }
            }

            System.out.println(
                    "\n========================================"
            );

            System.out.println(
                    "DOCUMENT PROCESSING COMPLETED"
            );

            System.out.println(
                    "Total chunks processed: "
                            + totalChunks
            );

            System.out.println(
                    "Total vectors stored: "
                            + vectorStoreService.size()
            );

            System.out.println(
                    "========================================\n"
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to process documents.",
                    e
            );
        }
    }
}