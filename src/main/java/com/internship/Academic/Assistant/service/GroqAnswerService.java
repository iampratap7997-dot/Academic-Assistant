package com.internship.Academic.Assistant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GroqAnswerService {

    private final RestClient restClient;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.model}")
    private String model;

    @Value("${groq.base.url}")
    private String baseUrl;

    public GroqAnswerService() {
        this.restClient = RestClient.builder()
                .build();
    }

    public String generateAnswer(
            String question,
            String context
    ) {

        String prompt = """
                You are an academic assistant for a college.

                Answer the user's question using ONLY the provided
                document context.

                STRICT RULES:
                1. Do not use outside knowledge.
                2. Do not invent or assume information.
                3. If the answer is not present in the context, say:
                   "I couldn't find this information in the available documents."
                4. Keep the answer clear and concise.
                5. Answer the exact question asked.
                6. Do not mention information that is unrelated to the question.
                7. If source/document information is present in the context,
                   mention it.
                
                DOCUMENT CONTEXT:
                %s

                USER QUESTION:
                %s
                """.formatted(context, question);

        Map<String, Object> requestBody = Map.of(
                "model", model,

                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content",
                                "You are a college academic assistant. "
                                        + "Use ONLY the supplied document context. "
                                        + "Never invent information."
                        ),

                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ),

                "temperature", 0.1,

                "max_tokens", 500
        );

        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {

                System.out.println(
                        "Groq answer request - attempt "
                                + attempt
                                + "/"
                                + maxAttempts
                );

                Map<?, ?> response = restClient
                        .post()
                        .uri(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(
                                "Authorization",
                                "Bearer " + apiKey
                        )
                        .body(requestBody)
                        .retrieve()
                        .body(Map.class);

                if (response == null) {
                    return "I couldn't generate an answer right now.";
                }

                Object choicesObject =
                        response.get("choices");

                if (!(choicesObject instanceof List<?> choices)
                        || choices.isEmpty()) {

                    return "I couldn't generate an answer right now.";
                }

                Object firstChoice = choices.get(0);

                if (!(firstChoice instanceof Map<?, ?> choice)) {
                    return "I couldn't generate an answer right now.";
                }

                Object messageObject =
                        choice.get("message");

                if (!(messageObject instanceof Map<?, ?> message)) {
                    return "I couldn't generate an answer right now.";
                }

                Object contentObject =
                        message.get("content");

                if (contentObject == null) {
                    return "I couldn't generate an answer right now.";
                }

                String answer =
                        contentObject.toString().trim();

                if (answer.isEmpty()) {
                    return "I couldn't generate an answer right now.";
                }

                System.out.println(
                        "Groq answer generated successfully."
                );

                return answer;

            } catch (RestClientResponseException e) {

                int statusCode =
                        e.getStatusCode().value();

                System.err.println(
                        "Groq API error: HTTP "
                                + statusCode
                                + " - "
                                + e.getStatusText()
                );

                /*
                 * Retry temporary errors.
                 *
                 * 429 = rate limit / quota
                 * 500 = server error
                 * 502 = bad gateway
                 * 503 = service unavailable
                 * 504 = gateway timeout
                 */
                if (statusCode == 429
                        || statusCode == 500
                        || statusCode == 502
                        || statusCode == 503
                        || statusCode == 504) {

                    if (attempt < maxAttempts) {

                        waitBeforeRetry(attempt);

                        continue;
                    }

                    return "Sorry, the AI service is temporarily busy. "
                            + "Please try again in a moment.";
                }

                /*
                 * Other errors are normally configuration/request
                 * problems and retrying will not help.
                 */
                return "Sorry, I couldn't process your question right now.";

            } catch (Exception e) {

                System.err.println(
                        "Groq request failed: "
                                + e.getClass().getSimpleName()
                                + " - "
                                + e.getMessage()
                );

                if (attempt < maxAttempts) {

                    waitBeforeRetry(attempt);

                    continue;
                }

                return "Sorry, the AI service is temporarily unavailable. "
                        + "Please try again in a moment.";
            }
        }

        return "Sorry, the AI service is temporarily unavailable. "
                + "Please try again in a moment.";
    }

    private void waitBeforeRetry(int attempt) {

        long delayMillis;

        if (attempt == 1) {
            delayMillis = 1000;
        } else {
            delayMillis = 2000;
        }

        try {

            Thread.sleep(delayMillis);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
        }
    }
}