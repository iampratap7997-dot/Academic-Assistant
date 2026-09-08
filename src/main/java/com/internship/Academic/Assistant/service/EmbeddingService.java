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

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

    private static final String EMBEDDING_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-embedding-001:embedContent";

    private static final String BATCH_EMBEDDING_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-embedding-001:batchEmbedContents";

    /*
     * Maximum number of chunks sent in one batch.
     *
     * 39 chunks will therefore normally require
     * only 2 Gemini requests.
     */
    private static final int BATCH_SIZE = 20;

    /*
     * Maximum number of attempts for a batch request.
     */
    private static final int MAX_RETRIES = 3;

    /*
     * Maximum time Gemini is allowed to take
     * for one HTTP request.
     */
    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(30);

    /*
     * Used for individual query embeddings.
     */
    public List<Double> embedQuery(String text) {

        return generateSingleEmbedding(
                text,
                "RETRIEVAL_QUERY"
        );
    }

    /*
     * Used when processing documents.
     *
     * This creates embeddings for multiple chunks
     * using batch requests.
     */
    public List<List<Double>> embedDocuments(
            List<String> texts
    ) {

        List<List<Double>> allEmbeddings =
                new ArrayList<>();

        if (texts == null || texts.isEmpty()) {

            return allEmbeddings;
        }

        System.out.println(
                "\nStarting batch embedding for "
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
                    "Embedding batch "
                            + (start / BATCH_SIZE + 1)
                            + " containing chunks "
                            + (start + 1)
                            + "-"
                            + end
            );

            List<List<Double>> embeddings =
                    generateBatchEmbeddings(
                            batch
                    );

            allEmbeddings.addAll(
                    embeddings
            );

            System.out.println(
                    "Batch completed successfully. "
                            + "Embeddings received: "
                            + embeddings.size()
            );
        }

        System.out.println(
                "All document embeddings generated. "
                        + "Total: "
                        + allEmbeddings.size()
        );

        return allEmbeddings;
    }

    /*
     * Generate embeddings for one batch.
     */
    private List<List<Double>> generateBatchEmbeddings(
            List<String> texts
    ) {

        String requestBody;

        try {

            StringBuilder requests =
                    new StringBuilder();

            requests.append(
                    "{\"requests\":["
            );

            for (int i = 0; i < texts.size(); i++) {

                if (i > 0) {
                    requests.append(",");
                }

                requests.append(
                        """
                        {
                          "model": "models/gemini-embedding-001",
                          "content": {
                            "parts": [
                              {
                                "text": %s
                              }
                            ]
                          },
                          "taskType": "RETRIEVAL_DOCUMENT",
                          "outputDimensionality": 768
                        }
                        """.formatted(
                                objectMapper.writeValueAsString(
                                        texts.get(i)
                                )
                        )
                );
            }

            requests.append("]}");

            requestBody =
                    requests.toString();

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to create batch embedding request.",
                    e
            );
        }

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

                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(
                                        URI.create(
                                                BATCH_EMBEDDING_URL
                                                        + "?key="
                                                        + apiKey
                                        )
                                )
                                .timeout(
                                        REQUEST_TIMEOUT
                                )
                                .header(
                                        "Content-Type",
                                        "application/json"
                                )
                                .POST(
                                        HttpRequest.BodyPublishers
                                                .ofString(
                                                        requestBody
                                                )
                                )
                                .build();

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers
                                        .ofString()
                        );

                int statusCode =
                        response.statusCode();

                /*
                 * Successful response.
                 */
                if (statusCode == 200) {

                    JsonNode root =
                            objectMapper.readTree(
                                    response.body()
                            );

                    JsonNode embeddingsNode =
                            root.path("embeddings");

                    if (!embeddingsNode.isArray()) {

                        throw new RuntimeException(
                                "Invalid batch embedding response: "
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
                                embeddingNode.path(
                                        "values"
                                );

                        if (!values.isArray()) {

                            throw new RuntimeException(
                                    "Invalid embedding values "
                                            + "in Gemini response."
                            );
                        }

                        List<Double> embedding =
                                new ArrayList<>();

                        for (
                                JsonNode value :
                                values
                        ) {

                            embedding.add(
                                    value.asDouble()
                            );
                        }

                        embeddings.add(
                                embedding
                        );
                    }

                    if (
                            embeddings.size()
                                    != texts.size()
                    ) {

                        throw new RuntimeException(
                                "Gemini returned "
                                        + embeddings.size()
                                        + " embeddings for "
                                        + texts.size()
                                        + " chunks."
                        );
                    }

                    System.out.println(
                            "Gemini batch embedding generated "
                                    + "successfully. Dimensions: "
                                    + embeddings
                                    .get(0)
                                    .size()
                    );

                    return embeddings;
                }

                /*
                 * Temporary errors.
                 */
                if (
                        statusCode == 429
                                || statusCode == 500
                                || statusCode == 502
                                || statusCode == 503
                                || statusCode == 504
                ) {

                    System.err.println(
                            "Temporary Gemini embedding error: "
                                    + statusCode
                    );

                    System.err.println(
                            "Response: "
                                    + response.body()
                    );

                    if (
                            attempt
                                    < MAX_RETRIES
                    ) {

                        waitBeforeRetry(
                                attempt
                        );

                        continue;
                    }
                }

                throw new RuntimeException(
                        "Gemini batch embedding API error: "
                                + statusCode
                                + " - "
                                + response.body()
                );

            } catch (
                    HttpTimeoutException e
            ) {

                System.err.println(
                        "Gemini batch embedding request timed out."
                );

                System.err.println(
                        "Attempt "
                                + attempt
                                + "/"
                                + MAX_RETRIES
                );

                if (
                        attempt
                                < MAX_RETRIES
                ) {

                    waitBeforeRetry(
                            attempt
                    );

                    continue;
                }

                throw new RuntimeException(
                        "Gemini batch embedding request "
                                + "timed out after "
                                + MAX_RETRIES
                                + " attempts.",
                        e
                );

            } catch (
                    IOException e
            ) {

                System.err.println(
                        "Network error while calling Gemini: "
                                + e.getMessage()
                );

                if (
                        attempt
                                < MAX_RETRIES
                ) {

                    waitBeforeRetry(
                            attempt
                    );

                    continue;
                }

                throw new RuntimeException(
                        "Failed to generate batch embeddings "
                                + "after "
                                + MAX_RETRIES
                                + " attempts.",
                        e
                );

            } catch (
                    InterruptedException e
            ) {

                Thread.currentThread().interrupt();

                throw new RuntimeException(
                        "Batch embedding request was interrupted.",
                        e
                );
            }
        }

        throw new RuntimeException(
                "Failed to generate batch embeddings."
        );
    }

    /*
     * Individual embedding.
     *
     * This is mainly used for the student's question
     * during retrieval.
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
                      "taskType": "%s",
                      "outputDimensionality": 768
                    }
                    """.formatted(
                            objectMapper.writeValueAsString(
                                    text
                            ),
                            taskType
                    );

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to create Gemini embedding request.",
                    e
            );
        }

        for (
                int attempt = 1;
                attempt <= MAX_RETRIES;
                attempt++
        ) {

            try {

                System.out.println(
                        "Sending Gemini query embedding request. "
                                + "Attempt "
                                + attempt
                                + "/"
                                + MAX_RETRIES
                );

                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(
                                        URI.create(
                                                EMBEDDING_URL
                                                        + "?key="
                                                        + apiKey
                                        )
                                )
                                .timeout(
                                        REQUEST_TIMEOUT
                                )
                                .header(
                                        "Content-Type",
                                        "application/json"
                                )
                                .POST(
                                        HttpRequest.BodyPublishers
                                                .ofString(
                                                        requestBody
                                                )
                                )
                                .build();

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers
                                        .ofString()
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

                    if (!values.isArray()) {

                        throw new RuntimeException(
                                "Invalid embedding response from Gemini."
                        );
                    }

                    List<Double> embedding =
                            new ArrayList<>();

                    for (
                            JsonNode value :
                            values
                    ) {

                        embedding.add(
                                value.asDouble()
                        );
                    }

                    System.out.println(
                            "Gemini query embedding generated. "
                                    + "Dimensions: "
                                    + embedding.size()
                    );

                    return embedding;
                }

                if (
                        statusCode == 429
                                || statusCode == 500
                                || statusCode == 502
                                || statusCode == 503
                                || statusCode == 504
                ) {

                    System.err.println(
                            "Temporary Gemini query embedding error: "
                                    + statusCode
                    );

                    if (
                            attempt
                                    < MAX_RETRIES
                    ) {

                        waitBeforeRetry(
                                attempt
                        );

                        continue;
                    }
                }

                throw new RuntimeException(
                        "Gemini embedding API error: "
                                + statusCode
                                + " - "
                                + response.body()
                );

            } catch (
                    HttpTimeoutException e
            ) {

                System.err.println(
                        "Gemini query embedding request timed out."
                );

                if (
                        attempt
                                < MAX_RETRIES
                ) {

                    waitBeforeRetry(
                            attempt
                    );

                    continue;
                }

                throw new RuntimeException(
                        "Gemini query embedding timed out.",
                        e
                );

            } catch (
                    IOException e
            ) {

                if (
                        attempt
                                < MAX_RETRIES
                ) {

                    waitBeforeRetry(
                            attempt
                    );

                    continue;
                }

                throw new RuntimeException(
                        "Failed to generate query embedding.",
                        e
                );

            } catch (
                    InterruptedException e
            ) {

                Thread.currentThread().interrupt();

                throw new RuntimeException(
                        "Query embedding request was interrupted.",
                        e
                );
            }
        }

        throw new RuntimeException(
                "Failed to generate query embedding."
        );
    }

    /*
     * Exponential backoff.
     */
    private void waitBeforeRetry(
            int attempt
    ) {

        long delay =
                (long)
                        Math.pow(
                                2,
                                attempt - 1
                        )
                        * 1000;

        System.out.println(
                "Retrying Gemini embedding request in "
                        + delay
                        + " ms..."
        );

        try {

            Thread.sleep(delay);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(
                    "Retry wait was interrupted.",
                    e
            );
        }
    }
}