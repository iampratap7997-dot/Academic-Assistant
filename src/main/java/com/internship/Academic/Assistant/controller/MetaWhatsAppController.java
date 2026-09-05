package com.internship.Academic.Assistant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.Academic.Assistant.service.MetaWhatsAppService;
import com.internship.Academic.Assistant.service.QuestionAnswerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp/meta")
public class MetaWhatsAppController {

    private final QuestionAnswerService questionAnswerService;
    private final MetaWhatsAppService metaWhatsAppService;
    private final ObjectMapper objectMapper;

    @Value("${meta.verify.token}")
    private String verifyToken;

    public MetaWhatsAppController(
            QuestionAnswerService questionAnswerService,
            MetaWhatsAppService metaWhatsAppService
    ) {
        this.questionAnswerService = questionAnswerService;
        this.metaWhatsAppService = metaWhatsAppService;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {

        System.out.println("Meta webhook verification request received.");

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {

            System.out.println("Meta webhook verification successful.");

            return ResponseEntity.ok(challenge);
        }

        System.out.println("Meta webhook verification failed.");

        return ResponseEntity.status(403).body("Forbidden");
    }

    @PostMapping
    public ResponseEntity<String> receiveWhatsAppMessage(
            @RequestBody String payload
    ) {

        System.out.println("Meta WhatsApp webhook received.");

        try {

            JsonNode root = objectMapper.readTree(payload);

            JsonNode messages = root
                    .path("entry")
                    .path(0)
                    .path("changes")
                    .path(0)
                    .path("value")
                    .path("messages");

            if (!messages.isArray() || messages.isEmpty()) {
                System.out.println("No WhatsApp message found in webhook.");
                return ResponseEntity.ok("EVENT_RECEIVED");
            }

            JsonNode message = messages.get(0);

            String messageType = message.path("type").asText();

            if (!"text".equals(messageType)) {
                System.out.println(
                        "Unsupported WhatsApp message type: " + messageType
                );
                return ResponseEntity.ok("EVENT_RECEIVED");
            }

            String sender = message.path("from").asText();

            String userMessage = message
                    .path("text")
                    .path("body")
                    .asText();

            System.out.println("WhatsApp sender: " + sender);
            System.out.println("WhatsApp message: " + userMessage);

            String answer = questionAnswerService.answer(userMessage);

            System.out.println("Generated answer: " + answer);

            metaWhatsAppService.sendTextMessage(sender, answer);

        } catch (Exception e) {

            System.err.println(
                    "Error processing Meta WhatsApp webhook."
            );

            e.printStackTrace();
        }

        return ResponseEntity.ok("EVENT_RECEIVED");
    }
}