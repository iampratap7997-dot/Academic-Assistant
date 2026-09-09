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

        /*
         * Groq receives ONLY the context retrieved from
         * our academic documents.
         *
         * It must not use outside knowledge.
         */
        String prompt = """
                You are an academic assistant for a college.

                Your job is to answer the user's question using ONLY
                the document context provided below.

                STRICT RULES:

                1. Use ONLY information present in the document context.

                2. Do NOT use your general knowledge.

                3. Do NOT search for or assume information that is
                   not present in the document context.

                4. Do NOT invent facts, dates, subjects, topics,
                   rules, holidays, or other information.

                5. Answer the exact question asked by the user.

                6. Keep the answer clear, natural, and concise.

                7. If the document context clearly contains the answer,
                   answer it directly.

                8. Do not mention information that is unrelated
                   to the user's question.

                9. Do not create a fallback message yourself.
                   The application handles questions for which
                   relevant document information cannot be found.

                DOCUMENT CONTEXT:
                %s

                USER QUESTION:
                %s
                """.formatted(context, question);

        Map<String, Object> requestBody = Map.of(

                "model", model,

                "messages", List.of(

                        Map.of(
                                "role",
                                "system",

                                "content",
                                "You are a college academic assistant. "
                                        + "Use ONLY the supplied document context. "
                                        + "Never use outside knowledge or invent information."
                        ),

                        Map.of(
                                "role",
                                "user",

                                "content",
                                prompt
                        )
                ),

                "reasoning_effort",
                "low",

                "include_reasoning",
                false,

                "max_completion_tokens",
                800
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

            /*
             * Check response.
             */
            if (response == null) {

                System.err.println(
                        "Groq returned a null response."
                );

                return "";
            }

            /*
             * Get choices.
             */
            Object choicesObject =
                    response.get("choices");

            if (!(choicesObject instanceof List<?> choices)
                    || choices.isEmpty()) {

                System.err.println(
                        "Groq response does not contain a valid choices list."
                );

                return "";
            }

            /*
             * Get first choice.
             */
            Object firstChoice =
                    choices.get(0);

            if (!(firstChoice instanceof Map<?, ?> choice)) {

                System.err.println(
                        "Groq first choice is not a JSON object."
                );

                return "";
            }

            /*
             * Get message.
             */
            Object messageObject =
                    choice.get("message");

            if (!(messageObject instanceof Map<?, ?> message)) {

                System.err.println(
                        "Groq response does not contain a valid message."
                );

                return "";
            }

            /*
             * Get generated content.
             */
            Object contentObject =
                    message.get("content");

            if (contentObject == null) {

                System.err.println(
                        "Groq message content is null."
                );

                return "";
            }

            String answer =
                    contentObject.toString().trim();

            /*
             * Empty answer.
             */
            if (answer.isEmpty()) {

                System.err.println(
                        "Groq returned empty content."
                );

                return "";
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

            /*
             * Return empty string instead of generating
             * a hard-coded fallback here.
             *
             * QuestionAnswerService will choose one
             * random fallback message.
             */
            return "";

        } catch (Exception e) {

            System.err.println(
                    "Groq request failed: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + e.getMessage()
            );

            return "";
        }
    }
}