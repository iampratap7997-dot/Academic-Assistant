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
import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String EMBEDDING_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-embedding-001:embedContent";

    /*
     * Maximum number of attempts for one embedding request.
     */
    private static final int MAX_RETRIES = 5;

    /*
     * Delay between normal embedding requests.
     *
     * This helps prevent hitting the per-minute quota.
     */
    private static final long REQUEST_DELAY_MS = 1000;

    public List<Double> embedDocument(String text) {
        return generateEmbedding(text, "RETRIEVAL_DOCUMENT");
    }

    public List<Double> embedQuery(String text) {
        return generateEmbedding(text, "RETRIEVAL_QUERY");
    }

    private List<Double> generateEmbedding(
            String text,
            String taskType
    ) {

        String requestBody;

        try {

            requestBody = """
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
                    objectMapper.writeValueAsString(text),
                    taskType
            );

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to create Gemini embedding request.",
                    e
            );
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

            try {

                /*
                 * Small delay before sending request.
                 *
                 * This prevents sending hundreds of requests
                 * immediately when processing many chunks.
                 */
                if (attempt == 1) {
                    Thread.sleep(REQUEST_DELAY_MS);
                }

                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(
                                        URI.create(
                                                EMBEDDING_URL
                                                        + "?key="
                                                        + apiKey
                                        )
                                )
                                .header(
                                        "Content-Type",
                                        "application/json"
                                )
                                .POST(
                                        HttpRequest.BodyPublishers
                                                .ofString(requestBody)
                                )
                                .build();

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                /*
                 * Successful response.
                 */
                if (response.statusCode() == 200) {

                    JsonNode root =
                            objectMapper.readTree(response.body());

                    JsonNode values =
                            root.path("embedding")
                                    .path("values");

                    if (!values.isArray()) {

                        throw new RuntimeException(
                                "Invalid embedding response from Gemini: "
                                        + response.body()
                        );
                    }

                    List<Double> embedding =
                            new ArrayList<>();

                    for (JsonNode value : values) {
                        embedding.add(value.asDouble());
                    }

                    return embedding;
                }

                /*
                 * 429 = quota/rate limit.
                 *
                 * Wait and retry.
                 */
                if (response.statusCode() == 429) {

                    System.out.println(
                            "Gemini embedding quota reached."
                    );

                    System.out.println(
                            "Retry attempt "
                                    + attempt
                                    + " of "
                                    + MAX_RETRIES
                    );

                    if (attempt < MAX_RETRIES) {

                        long waitTime =
                                (long) Math.pow(2, attempt) * 2000;

                        System.out.println(
                                "Waiting "
                                        + waitTime
                                        + " ms before retry..."
                        );

                        Thread.sleep(waitTime);

                        continue;
                    }

                    throw new RuntimeException(
                            "Gemini Embedding API quota exceeded after "
                                    + MAX_RETRIES
                                    + " attempts.\n"
                                    + response.body()
                    );
                }

                /*
                 * Other API errors should not be retried.
                 */
                throw new RuntimeException(
                        "Gemini Embedding API error: "
                                + response.statusCode()
                                + " - "
                                + response.body()
                );

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                throw new RuntimeException(
                        "Embedding request was interrupted.",
                        e
                );

            } catch (IOException e) {

                /*
                 * Network error.
                 *
                 * Retry if attempts are remaining.
                 */
                if (attempt < MAX_RETRIES) {

                    try {

                        long waitTime =
                                (long) Math.pow(2, attempt) * 1000;

                        System.out.println(
                                "Network error while calling Gemini."
                        );

                        System.out.println(
                                "Retrying in "
                                        + waitTime
                                        + " ms..."
                        );

                        Thread.sleep(waitTime);

                    } catch (InterruptedException interruptedException) {

                        Thread.currentThread().interrupt();

                        throw new RuntimeException(
                                "Embedding request interrupted.",
                                interruptedException
                        );
                    }

                } else {

                    throw new RuntimeException(
                            "Failed to generate embedding after "
                                    + MAX_RETRIES
                                    + " attempts.",
                            e
                    );
                }
            }
        }

        throw new RuntimeException(
                "Failed to generate embedding."
        );
    }
}
