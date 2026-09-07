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
import java.time.Duration;

@Service
public class GeminiAnswerService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-3.6-flash:generateContent";

    private static final int MAX_ATTEMPTS = 3;

    public String generateAnswer(String question, String context) {

        String prompt = """
                You are an academic assistant.

                Answer the student's question using ONLY the information
                provided in the context below.

                If the answer cannot be found in the context, say:
                "I couldn't find this information in the available documents."

                Keep the answer clear, accurate and easy for a student to understand.

                Context:
                %s

                Student Question:
                %s
                """.formatted(context, question);

        try {

            String requestBody = """
                    {
                      "contents": [
                        {
                          "parts": [
                            {
                              "text": %s
                            }
                          ]
                        }
                      ]
                    }
                    """.formatted(
                    objectMapper.writeValueAsString(prompt)
            );

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            GEMINI_URL
                                                    + "?key="
                                                    + apiKey
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .timeout(Duration.ofSeconds(20))
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(requestBody)
                            )
                            .build();

            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

                System.out.println(
                        "Sending Gemini answer request. Attempt "
                                + attempt
                                + "/"
                                + MAX_ATTEMPTS
                );

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                int statusCode = response.statusCode();

                if (statusCode == 200) {

                    JsonNode root =
                            objectMapper.readTree(response.body());

                    JsonNode candidates = root.path("candidates");

                    if (candidates.isArray() && !candidates.isEmpty()) {

                        JsonNode textNode =
                                candidates
                                        .get(0)
                                        .path("content")
                                        .path("parts")
                                        .get(0)
                                        .path("text");

                        if (!textNode.isMissingNode()) {

                            System.out.println(
                                    "Gemini answer generated successfully."
                            );

                            return textNode.asText();
                        }
                    }

                    throw new RuntimeException(
                            "Gemini returned an empty answer."
                    );
                }

                // Retry temporary Gemini errors.
                if (statusCode == 429 || statusCode == 500 || statusCode == 502
                        || statusCode == 503 || statusCode == 504) {

                    System.err.println(
                            "Gemini temporary error: "
                                    + statusCode
                                    + "."
                    );

                    if (attempt < MAX_ATTEMPTS) {

                        long delay =
                                (long) Math.pow(2, attempt - 1) * 1000;

                        System.out.println(
                                "Retrying Gemini answer request in "
                                        + delay
                                        + " ms..."
                        );

                        Thread.sleep(delay);
                        continue;
                    }
                }

                // Non-retryable error.
                throw new RuntimeException(
                        "Gemini answer API error: "
                                + statusCode
                                + " - "
                                + response.body()
                );
            }

            throw new RuntimeException(
                    "Gemini answer request failed after "
                            + MAX_ATTEMPTS
                            + " attempts."
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(
                    "Gemini answer request was interrupted.",
                    e
            );

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to communicate with Gemini.",
                    e
            );
        }
    }
}