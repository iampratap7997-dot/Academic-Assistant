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

    /*
     * Maximum number of Gemini attempts.
     */
    private static final int MAX_ATTEMPTS = 3;

    /*
     * Maximum time allowed for one Gemini request.
     */
    private static final int REQUEST_TIMEOUT_SECONDS = 15;

    public String generateAnswer(String question, String context) {

        String prompt = """
                You are an academic assistant.

                Answer the student's question using ONLY the information
                provided in the context below.

                If the answer cannot be found in the context, say:
                "I couldn't find this information in the available documents."

                Do not use outside knowledge.

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
                            .timeout(
                                    Duration.ofSeconds(
                                            REQUEST_TIMEOUT_SECONDS
                                    )
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            requestBody
                                    )
                            )
                            .build();

            for (int attempt = 1;
                 attempt <= MAX_ATTEMPTS;
                 attempt++) {

                try {

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

                    /*
                     * SUCCESS
                     */
                    if (statusCode == 200) {

                        JsonNode root =
                                objectMapper.readTree(
                                        response.body()
                                );

                        JsonNode candidates =
                                root.path("candidates");

                        if (candidates.isArray()
                                && !candidates.isEmpty()) {

                            JsonNode parts =
                                    candidates
                                            .get(0)
                                            .path("content")
                                            .path("parts");

                            if (parts.isArray()
                                    && !parts.isEmpty()) {

                                JsonNode textNode =
                                        parts
                                                .get(0)
                                                .path("text");

                                if (!textNode.isMissingNode()
                                        && !textNode.isNull()) {

                                    String answer =
                                            textNode.asText();

                                    if (!answer.isBlank()) {

                                        System.out.println(
                                                "Gemini answer generated successfully."
                                        );

                                        return answer;
                                    }
                                }
                            }
                        }

                        System.err.println(
                                "Gemini returned an empty answer."
                        );

                        return "I couldn't generate an answer right now.";
                    }

                    /*
                     * TEMPORARY GEMINI ERRORS
                     *
                     * These can sometimes happen because of
                     * temporary overload or rate limits.
                     */
                    if (statusCode == 429
                            || statusCode == 500
                            || statusCode == 502
                            || statusCode == 503
                            || statusCode == 504) {

                        System.err.println(
                                "Gemini temporary error: "
                                        + statusCode
                        );

                        if (attempt < MAX_ATTEMPTS) {

                            long delay =
                                    (long)
                                            Math.pow(
                                                    2,
                                                    attempt - 1
                                            )
                                            * 1000;

                            System.out.println(
                                    "Retrying Gemini answer request in "
                                            + delay
                                            + " ms..."
                            );

                            Thread.sleep(delay);

                            continue;
                        }

                        System.err.println(
                                "Gemini failed after "
                                        + MAX_ATTEMPTS
                                        + " attempts."
                        );

                        return "Sorry, Gemini is temporarily unavailable. Please try again in a moment.";
                    }

                    /*
                     * NON-RETRYABLE ERROR
                     */
                    System.err.println(
                            "Gemini answer API error: "
                                    + statusCode
                    );

                    System.err.println(
                            "Gemini response: "
                                    + response.body()
                    );

                    return "Sorry, I couldn't process your question right now.";

                } catch (java.net.http.HttpTimeoutException e) {

                    System.err.println(
                            "Gemini answer request timed out on attempt "
                                    + attempt
                                    + "/"
                                    + MAX_ATTEMPTS
                    );

                    if (attempt < MAX_ATTEMPTS) {

                        long delay =
                                (long)
                                        Math.pow(
                                                2,
                                                attempt - 1
                                        )
                                        * 1000;

                        System.out.println(
                                "Retrying after timeout in "
                                        + delay
                                        + " ms..."
                        );

                        Thread.sleep(delay);

                    } else {

                        System.err.println(
                                "Gemini answer request timed out after all attempts."
                        );

                        return "Sorry, the AI service is taking too long to respond. Please try again.";
                    }

                } catch (IOException e) {

                    System.err.println(
                            "Network error while communicating with Gemini: "
                                    + e.getMessage()
                    );

                    if (attempt < MAX_ATTEMPTS) {

                        long delay =
                                (long)
                                        Math.pow(
                                                2,
                                                attempt - 1
                                        )
                                        * 1000;

                        System.out.println(
                                "Retrying Gemini request in "
                                        + delay
                                        + " ms..."
                        );

                        Thread.sleep(delay);

                    } else {

                        return "Sorry, I couldn't connect to the AI service right now.";
                    }
                }
            }

            return "Sorry, I couldn't generate an answer right now.";

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            System.err.println(
                    "Gemini answer request was interrupted."
            );

            return "Sorry, the request was interrupted. Please try again.";

        } catch (Exception e) {

            System.err.println(
                    "Unexpected error while generating Gemini answer."
            );

            e.printStackTrace();

            return "Sorry, I couldn't process your question right now.";
        }
    }
}