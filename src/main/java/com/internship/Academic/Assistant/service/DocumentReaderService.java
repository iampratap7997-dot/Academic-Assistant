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

    private final Tika tika = new Tika();
    private final DocumentChunkingService chunkingService;

    public DocumentReaderService(
            DocumentChunkingService chunkingService
    ) {
        this.chunkingService = chunkingService;
    }

    public void processDocuments() {

        try {

            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();

            Resource[] resources =
                    resolver.getResources("classpath:/documents/*");

            for (Resource resource : resources) {

                try (InputStream inputStream =
                             resource.getInputStream()) {

                    String text = tika.parseToString(inputStream);

                    List<DocumentChunk> chunks =
                            chunkingService.createChunks(
                                    resource.getFilename(),
                                    text
                            );

                    System.out.println(
                            "\n========================================"
                    );

                    System.out.println(
                            "Document: " + resource.getFilename()
                    );

                    System.out.println(
                            "Total chunks: " + chunks.size()
                    );

                    System.out.println(
                            "========================================"
                    );

                    for (DocumentChunk chunk : chunks) {

                        System.out.println(
                                "Chunk "
                                        + chunk.getChunkNumber()
                                        + " | "
                                        + chunk.getContent().length()
                                        + " characters"
                        );
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}