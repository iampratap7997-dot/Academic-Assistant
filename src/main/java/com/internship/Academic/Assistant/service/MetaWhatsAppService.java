package com.internship.Academic.Assistant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class MetaWhatsAppService {

    @Value("${meta.access.token}")
    private String accessToken;

    @Value("${meta.phone.number.id}")
    private String phoneNumberId;

    @Value("${meta.graph.api.version}")
    private String graphApiVersion;

    private final RestClient restClient;

    public MetaWhatsAppService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://graph.facebook.com")
                .build();
    }

    public void sendTextMessage(String recipientPhoneNumber, String message) {

        String url = "/" + graphApiVersion + "/" + phoneNumberId + "/messages";

        Map<String, Object> requestBody = Map.of(
                "messaging_product", "whatsapp",
                "to", recipientPhoneNumber,
                "type", "text",
                "text", Map.of(
                        "body", message
                )
        );

        try {

            String response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            System.out.println("Meta WhatsApp response: " + response);

        } catch (Exception e) {

            System.err.println("Failed to send WhatsApp message through Meta.");
            System.err.println("Error: " + e.getMessage());

        }
    }
}
