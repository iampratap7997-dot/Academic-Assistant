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

    /*
     * HTTP client with a connection timeout.
     *
     * This prevents the application from waiting forever
     * while trying to connect to Gemini.
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final String EMBEDDING_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-embedding-001:embedContent";

    /*
     * Maximum number of attempts for one embedding request.
     */
    private static final int MAX_RETRIES = 5;

    /*
     * Maximum time allowed for Gemini to respond to
     * one embedding request.
     *
     * This is the important fix for the Render freeze.
     */
    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(30);

    /*
     * Delay between normal embedding requests.
     *
     * This helps avoid hitting Gemini rate limits
     * when processing many document chunks.
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
                 * Wait between requests.
                 *
                 * We do not wait before the very first request.
                 */
                if (attempt == 1) {

                    Thread.sleep(REQUEST_DELAY_MS);

                } else {

                    long retryDelay =
                            (long) Math.pow(2, attempt - 1) * 2000;

                    System.out.println(
                            "Waiting "
                                    + retryDelay
                                    + " ms before retry..."
                    );

                    Thread.sleep(retryDelay);
                }

                System.out.println(
                        "Sending Gemini embedding request. "
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
                                .timeout(REQUEST_TIMEOUT)
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

                int statusCode = response.statusCode();

                /*
                 * Successful response.
                 */
                if (statusCode == 200) {

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

                    System.out.println(
                            "Gemini embedding generated successfully. "
                                    + "Dimensions: "
                                    + embedding.size()
                    );

                    return embedding;
                }

                /*
                 * 429 = quota/rate limit.
                 *
                 * Retry because the problem may be temporary.
                 */
                if (statusCode == 429) {

                    System.out.println(
                            "Gemini embedding quota/rate limit reached."
                    );

                    System.out.println(
                            "Attempt "
                                    + attempt
                                    + "/"
                                    + MAX_RETRIES
                    );

                    System.out.println(
                            "Gemini response: "
                                    + response.body()
                    );

                    if (attempt < MAX_RETRIES) {

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
                 * 5xx errors can be temporary.
                 *
                 * Retry them instead of immediately failing.
                 */
                if (statusCode >= 500 && statusCode <= 599) {

                    System.out.println(
                            "Gemini server error: "
                                    + statusCode
                    );

                    System.out.println(
                            "Response: "
                                    + response.body()
                    );

                    if (attempt < MAX_RETRIES) {

                        continue;
                    }

                    throw new RuntimeException(
                            "Gemini Embedding API server error after "
                                    + MAX_RETRIES
                                    + " attempts: "
                                    + statusCode
                                    + " - "
                                    + response.body()
                    );
                }

                /*
                 * Other API errors are not retried.
                 */
                throw new RuntimeException(
                        "Gemini Embedding API error: "
                                + statusCode
                                + " - "
                                + response.body()
                );

            } catch (HttpTimeoutException e) {

                /*
                 * IMPORTANT:
                 *
                 * If Gemini takes more than 30 seconds,
                 * the request is cancelled instead of hanging
                 * forever.
                 */
                System.err.println(
                        "Gemini embedding request timed out."
                );

                System.err.println(
                        "Attempt "
                                + attempt
                                + "/"
                                + MAX_RETRIES
                );

                if (attempt == MAX_RETRIES) {

                    throw new RuntimeException(
                            "Gemini embedding request timed out after "
                                    + MAX_RETRIES
                                    + " attempts.",
                            e
                    );
                }

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
                 * Retry if attempts remain.
                 */
                System.err.println(
                        "Network error while calling Gemini: "
                                + e.getMessage()
                );

                System.err.println(
                        "Attempt "
                                + attempt
                                + "/"
                                + MAX_RETRIES
                );

                if (attempt == MAX_RETRIES) {

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