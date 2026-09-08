package com.internship.Academic.Assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    private static final String SINGLE_EMBEDDING_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-embedding-001:embedContent";

    private static final String BATCH_EMBEDDING_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-embedding-001:batchEmbedContents";

    private static final int BATCH_SIZE = 20;

    private static final int MAX_RETRIES = 3;

    private static final int REQUEST_TIMEOUT_SECONDS = 30;

    /**
     * Existing method kept for compatibility with the existing tests
     * and any other code that may call embedDocument().
     */
    public List<Double> embedDocument(String text) {

        return generateSingleEmbedding(
                text,
                "RETRIEVAL_DOCUMENT"
        );
    }

    /**
     * Creates an embedding for a user question.
     */
    public List<Double> embedQuery(String text) {

        return generateSingleEmbedding(
                text,
                "RETRIEVAL_QUERY"
        );
    }

    /**
     * Creates embeddings for multiple document chunks.
     *
     * Instead of sending one API request for every chunk,
     * chunks are grouped into batches.
     */
    public List<List<Double>> embedDocuments(
            List<String> texts
    ) {

        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<List<Double>> allEmbeddings =
                new ArrayList<>();

        System.out.println(
                "Starting batch embedding for "
                        + texts.size()
                        + " chunks."
        );

        for (
                int start = 0;
                start < texts.size();
                start += BATCH_SIZE
        ) {

            int end =
                    Math.min(
                            start + BATCH_SIZE,
                            texts.size()
                    );

            List<String> batch =
                    texts.subList(start, end);

            System.out.println(
                    "Embedding batch containing chunks "
                            + (start + 1)
                            + "-"
                            + end
                            + " / "
                            + texts.size()
            );

            List<List<Double>> batchEmbeddings =
                    generateBatchEmbeddings(batch);

            allEmbeddings.addAll(batchEmbeddings);

            System.out.println(
                    "Batch completed successfully. "
                            + "Embeddings received: "
                            + batchEmbeddings.size()
            );
        }

        if (allEmbeddings.size() != texts.size()) {

            throw new RuntimeException(
                    "Number of embeddings does not match "
                            + "number of document chunks. "
                            + "Expected: "
                            + texts.size()
                            + ", received: "
                            + allEmbeddings.size()
            );
        }

        System.out.println(
                "All document embeddings generated successfully."
        );

        return allEmbeddings;
    }

    /**
     * Generates one embedding using Gemini's embedContent endpoint.
     */
    private List<Double> generateSingleEmbedding(
            String text,
            String taskType
    ) {

        String requestBody;

        try {

            requestBody =
                    """
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "taskType": "%s"
                    }
                    """.formatted(
                            objectMapper.writeValueAsString(text),
                            taskType
                    );

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to create Gemini embedding request.",
                    e
            );
        }

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        SINGLE_EMBEDDING_URL
                                                + "?key="
                                                + apiKey
                                )
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        requestBody
                                )
                        )
                        .timeout(
                                Duration.ofSeconds(
                                        REQUEST_TIMEOUT_SECONDS
                                )
                        )
                        .build();

        for (
                int attempt = 1;
                attempt <= MAX_RETRIES;
                attempt++
        ) {

            try {

                System.out.println(
                        "Sending Gemini embedding request. "
                                + "Attempt "
                                + attempt
                                + "/"
                                + MAX_RETRIES
                );

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                int statusCode =
                        response.statusCode();

                if (statusCode == 200) {

                    JsonNode root =
                            objectMapper.readTree(
                                    response.body()
                            );

                    JsonNode values =
                            root.path("embedding")
                                    .path("values");

                    if (
                            values.isArray()
                                    && !values.isEmpty()
                    ) {

                        List<Double> embedding =
                                new ArrayList<>();

                        for (JsonNode value : values) {

                            embedding.add(
                                    value.asDouble()
                            );
                        }

                        System.out.println(
                                "Gemini embedding generated successfully."
                        );

                        return embedding;
                    }

                    throw new RuntimeException(
                            "Gemini returned an empty embedding."
                    );
                }

                if (isRetryableStatus(statusCode)) {

                    System.err.println(
                            "Gemini temporary embedding error: "
                                    + statusCode
                    );

                    if (attempt < MAX_RETRIES) {

                        waitBeforeRetry(attempt);

                        continue;
                    }
                }

                throw new RuntimeException(
                        "Gemini embedding API error: "
                                + statusCode
                                + " - "
                                + response.body()
                );

            } catch (HttpTimeoutException e) {

                System.err.println(
                        "Gemini embedding request timed out."
                );

                if (attempt < MAX_RETRIES) {

                    waitBeforeRetry(attempt);

                    continue;
                }

                throw new RuntimeException(
                        "Gemini embedding request timed out "
                                + "after "
                                + MAX_RETRIES
                                + " attempts.",
                        e
                );

            } catch (IOException e) {

                if (attempt < MAX_RETRIES) {

                    System.err.println(
                            "Temporary network error while "
                                    + "calling Gemini."
                    );

                    waitBeforeRetry(attempt);

                    continue;
                }

                throw new RuntimeException(
                        "Failed to communicate with Gemini "
                                + "embedding API.",
                        e
                );

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                throw new RuntimeException(
                        "Gemini embedding request was interrupted.",
                        e
                );
            }
        }

        throw new RuntimeException(
                "Gemini embedding request failed."
        );
    }

    /**
     * Generates embeddings for a batch of document chunks.
     */
    private List<List<Double>> generateBatchEmbeddings(
            List<String> texts
    ) {

        List<Object> requests =
                new ArrayList<>();

        for (String text : texts) {

            requests.add(
                    new BatchEmbeddingRequest(
                            "models/gemini-embedding-001",
                            new Content(
                                    List.of(
                                            new Part(text)
                                    )
                            ),
                            "RETRIEVAL_DOCUMENT"
                    )
            );
        }

        String requestBody;

        try {

            requestBody =
                    objectMapper.writeValueAsString(
                            new BatchEmbeddingRequestWrapper(
                                    requests
                            )
                    );

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to create Gemini batch embedding request.",
                    e
            );
        }

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        BATCH_EMBEDDING_URL
                                                + "?key="
                                                + apiKey
                                )
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        requestBody
                                )
                        )
                        .timeout(
                                Duration.ofSeconds(
                                        REQUEST_TIMEOUT_SECONDS
                                )
                        )
                        .build();

        for (
                int attempt = 1;
                attempt <= MAX_RETRIES;
                attempt++
        ) {

            try {

                System.out.println(
                        "Sending Gemini batch embedding request. "
                                + "Attempt "
                                + attempt
                                + "/"
                                + MAX_RETRIES
                );

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                int statusCode =
                        response.statusCode();

                if (statusCode == 200) {

                    JsonNode root =
                            objectMapper.readTree(
                                    response.body()
                            );

                    JsonNode embeddingsNode =
                            root.path("embeddings");

                    if (
                            !embeddingsNode.isArray()
                                    || embeddingsNode.isEmpty()
                    ) {

                        throw new RuntimeException(
                                "Gemini returned an empty "
                                        + "batch embedding response. "
                                        + "Response: "
                                        + response.body()
                        );
                    }

                    List<List<Double>> embeddings =
                            new ArrayList<>();

                    for (
                            JsonNode embeddingNode :
                            embeddingsNode
                    ) {

                        JsonNode values =
                                embeddingNode.path("values");

                        if (
                                !values.isArray()
                                        || values.isEmpty()
                        ) {

                            throw new RuntimeException(
                                    "Gemini returned an invalid "
                                            + "embedding in the batch."
                            );
                        }

                        List<Double> embedding =
                                new ArrayList<>();

                        for (JsonNode value : values) {

                            embedding.add(
                                    value.asDouble()
                            );
                        }

                        embeddings.add(embedding);
                    }

                    return embeddings;
                }

                if (isRetryableStatus(statusCode)) {

                    System.err.println(
                            "Gemini temporary batch embedding error: "
                                    + statusCode
                    );

                    if (attempt < MAX_RETRIES) {

                        waitBeforeRetry(attempt);

                        continue;
                    }
                }

                throw new RuntimeException(
                        "Gemini batch embedding API error: "
                                + statusCode
                                + " - "
                                + response.body()
                );

            } catch (HttpTimeoutException e) {

                System.err.println(
                        "Gemini batch embedding request timed out."
                );

                if (attempt < MAX_RETRIES) {

                    waitBeforeRetry(attempt);

                    continue;
                }

                throw new RuntimeException(
                        "Gemini batch embedding request timed out "
                                + "after "
                                + MAX_RETRIES
                                + " attempts.",
                        e
                );

            } catch (IOException e) {

                if (attempt < MAX_RETRIES) {

                    System.err.println(
                            "Temporary network error while "
                                    + "calling Gemini batch embedding."
                    );

                    waitBeforeRetry(attempt);

                    continue;
                }

                throw new RuntimeException(
                        "Failed to communicate with Gemini "
                                + "batch embedding API.",
                        e
                );

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                throw new RuntimeException(
                        "Gemini batch embedding request "
                                + "was interrupted.",
                        e
                );
            }
        }

        throw new RuntimeException(
                "Gemini batch embedding request failed."
        );
    }

    private boolean isRetryableStatus(
            int statusCode
    ) {

        return statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private void waitBeforeRetry(
            int attempt
    ) throws InterruptedException {

        long delay =
                (long) Math.pow(
                        2,
                        attempt - 1
                ) * 1000;

        System.out.println(
                "Retrying Gemini embedding request in "
                        + delay
                        + " ms..."
        );

        Thread.sleep(delay);
    }

    /*
     * Helper classes used to create the batch JSON.
     */

    private record BatchEmbeddingRequestWrapper(
            List<Object> requests
    ) {}

    private record BatchEmbeddingRequest(
            String model,
            Content content,
            String taskType
    ) {}

    private record Content(
            List<Part> parts
    ) {}

    private record Part(
            String text
    ) {}
}