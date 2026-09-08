package com.internship.Academic.Assistant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
        this.restClient = RestClient.builder().build();
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
                5. Answer only the exact question.
                6. Do not mention unrelated information.

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

                "reasoning_effort", "low",
                "include_reasoning", false,
                "max_completion_tokens", 800
        );

        try {

            System.out.println(
                    "Groq answer request - model: " + model
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

            System.out.println(
                    "Groq raw response: " + response
            );

            if (response == null) {
                System.err.println(
                        "Groq returned a null response."
                );

                return "I couldn't generate an answer right now.";
            }

            Object choicesObject =
                    response.get("choices");

            if (!(choicesObject instanceof List<?> choices)
                    || choices.isEmpty()) {

                System.err.println(
                        "Groq response does not contain a valid choices list."
                );

                return "I couldn't generate an answer right now.";
            }

            Object firstChoice = choices.get(0);

            if (!(firstChoice instanceof Map<?, ?> choice)) {

                System.err.println(
                        "Groq first choice is not a JSON object."
                );

                return "I couldn't generate an answer right now.";
            }

            Object messageObject =
                    choice.get("message");

            if (!(messageObject instanceof Map<?, ?> message)) {

                System.err.println(
                        "Groq response does not contain a valid message."
                );

                return "I couldn't generate an answer right now.";
            }

            Object contentObject =
                    message.get("content");

            if (contentObject == null) {

                System.err.println(
                        "Groq message content is null."
                );

                return "I couldn't generate an answer right now.";
            }

            String answer =
                    contentObject.toString().trim();

            if (answer.isEmpty()) {

                System.err.println(
                        "Groq returned empty content."
                );

                return "I couldn't generate an answer right now.";
            }

            System.out.println(
                    "Groq answer generated successfully."
            );

            return answer;

        } catch (RestClientResponseException e) {

            System.err.println(
                    "Groq API error: HTTP "
                            + e.getStatusCode().value()
            );

            System.err.println(
                    "Groq response body: "
                            + e.getResponseBodyAsString()
            );

            return "Sorry, I couldn't process your question right now.";

        } catch (Exception e) {

            System.err.println(
                    "Groq request failed: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + e.getMessage()
            );

            return "I couldn't generate an answer right now.";
        }
    }
}