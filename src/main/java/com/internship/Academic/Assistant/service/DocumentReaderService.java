package com.internship.Academic.Assistant.service;

import com.internship.Academic.Assistant.model.DocumentChunk;
import com.internship.Academic.Assistant.repository.DocumentChunkRepository;
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
    private final DocumentChunkRepository chunkRepository;

    public DocumentReaderService(
            DocumentChunkingService chunkingService,
            DocumentChunkRepository chunkRepository
    ) {
        this.chunkingService = chunkingService;
        this.chunkRepository = chunkRepository;
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

                    chunkRepository.saveAll(chunks);

                    System.out.println(
                            "\n========================================"
                    );

                    System.out.println(
                            "Document: " + resource.getFilename()
                    );

                    System.out.println(
                            "Chunks created: " + chunks.size()
                    );

                    System.out.println(
                            "========================================"
                    );
                }
            }

            System.out.println(
                    "\nTotal chunks stored: "
                            + chunkRepository.count()
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}