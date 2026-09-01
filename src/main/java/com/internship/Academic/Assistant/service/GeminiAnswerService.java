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

@Service
public class GeminiAnswerService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-3.6-flash";

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
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(requestBody)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() != 200) {

                throw new RuntimeException(
                        "Gemini answer API error: "
                                + response.statusCode()
                                + " - "
                                + response.body()
                );
            }

            JsonNode root =
                    objectMapper.readTree(response.body());

            return root
                    .path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

        } catch (IOException | InterruptedException e) {

            throw new RuntimeException(
                    "Failed to communicate with Gemini.",
                    e
            );
        }
    }
}