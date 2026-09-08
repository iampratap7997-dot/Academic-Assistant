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

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-3.6-flash:generateContent";

    /*
     * Number of attempts for temporary Gemini failures.
     */
    private static final int MAX_ATTEMPTS = 3;

    /*
     * Maximum time allowed for one Gemini request.
     */
    private static final int REQUEST_TIMEOUT_SECONDS = 15;

    public String generateAnswer(
            String question,
            String context
    ) {

        /*
         * Gemini is strictly instructed to use only
         * the information retrieved from our documents.
         */
        String prompt = """
                You are an academic assistant for a college.

                IMPORTANT RULES:

                1. Answer ONLY using the information in the provided context.
                2. Do NOT use outside knowledge.
                3. Do NOT guess or assume anything.
                4. If the answer is not present in the context, say exactly:
                   "I couldn't find this information in the available documents."
                5. Keep the answer clear, short and easy for a college student
                   to understand.
                6. Do not mention that you are an AI model.
                7. Do not make up information.
                8. If the context contains a direct answer, use that answer
                   accurately.

                CONTEXT FROM OFFICIAL COLLEGE DOCUMENTS:
                %s

                STUDENT QUESTION:
                %s

                ANSWER:
                """.formatted(
                context,
                question
        );

        try {

            String requestBody =
                    """
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

            for (
                    int attempt = 1;
                    attempt <= MAX_ATTEMPTS;
                    attempt++
            ) {

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

                    int statusCode =
                            response.statusCode();

                    /*
                     * ==========================
                     * SUCCESS
                     * ==========================
                     */
                    if (statusCode == 200) {

                        JsonNode root =
                                objectMapper.readTree(
                                        response.body()
                                );

                        JsonNode candidates =
                                root.path("candidates");

                        if (
                                candidates.isArray()
                                        && !candidates.isEmpty()
                        ) {

                            JsonNode parts =
                                    candidates
                                            .get(0)
                                            .path("content")
                                            .path("parts");

                            if (
                                    parts.isArray()
                                            && !parts.isEmpty()
                            ) {

                                JsonNode textNode =
                                        parts
                                                .get(0)
                                                .path("text");

                                if (
                                        !textNode.isMissingNode()
                                                && !textNode.isNull()
                                ) {

                                    String answer =
                                            textNode.asText().trim();

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
                     * ==========================
                     * RATE LIMIT / TEMPORARY ERROR
                     * ==========================
                     *
                     * 429 = rate limit / quota
                     * 500 = internal server error
                     * 502 = bad gateway
                     * 503 = service unavailable
                     * 504 = gateway timeout
                     */
                    if (
                            statusCode == 429
                                    || statusCode == 500
                                    || statusCode == 502
                                    || statusCode == 503
                                    || statusCode == 504
                    ) {

                        System.err.println(
                                "Gemini temporary error: "
                                        + statusCode
                        );

                        /*
                         * For 429, waiting longer is useful because
                         * it normally means rate limiting.
                         *
                         * For other temporary errors we use
                         * shorter exponential backoff.
                         */
                        long delay;

                        if (statusCode == 429) {

                            delay =
                                    switch (attempt) {
                                        case 1 -> 5000;
                                        case 2 -> 10000;
                                        default -> 20000;
                                    };

                        } else {

                            delay =
                                    switch (attempt) {
                                        case 1 -> 2000;
                                        case 2 -> 5000;
                                        default -> 10000;
                                    };
                        }

                        if (attempt < MAX_ATTEMPTS) {

                            System.out.println(
                                    "Retrying Gemini request in "
                                            + delay
                                            + " ms..."
                            );

                            Thread.sleep(delay);
                        }

                        continue;
                    }

                    /*
                     * ==========================
                     * NON-RETRYABLE ERROR
                     * ==========================
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

                } catch (
                        java.net.http.HttpTimeoutException e
                ) {

                    System.err.println(
                            "Gemini request timed out. Attempt "
                                    + attempt
                                    + "/"
                                    + MAX_ATTEMPTS
                    );

                    if (attempt < MAX_ATTEMPTS) {

                        long delay =
                                switch (attempt) {
                                    case 1 -> 2000;
                                    case 2 -> 5000;
                                    default -> 10000;
                                };

                        System.out.println(
                                "Retrying after timeout in "
                                        + delay
                                        + " ms..."
                        );

                        Thread.sleep(delay);

                    } else {

                        System.err.println(
                                "Gemini request timed out after all attempts."
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
                                switch (attempt) {
                                    case 1 -> 2000;
                                    case 2 -> 5000;
                                    default -> 10000;
                                };

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

            /*
             * All attempts failed.
             */
            return "Sorry, Gemini is temporarily unavailable. Please try again in a moment.";

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