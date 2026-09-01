package com.internship.Academic.Assistant.controller;

import com.internship.Academic.Assistant.service.QuestionAnswerService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp")
@CrossOrigin(origins = "*")
public class WhatsAppController {

    private final QuestionAnswerService questionAnswerService;

    public WhatsAppController(QuestionAnswerService questionAnswerService) {
        this.questionAnswerService = questionAnswerService;
    }

    @PostMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> receiveMessage(
            @RequestParam("Body") String message
    ) {

        System.out.println("WhatsApp message: " + message);

        String answer = questionAnswerService.answer(message);

        String response = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                    <Message>%s</Message>
                </Response>
                """.formatted(answer);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(response);
    }
}