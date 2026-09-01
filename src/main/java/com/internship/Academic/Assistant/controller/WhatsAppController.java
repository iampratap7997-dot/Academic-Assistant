package com.internship.Academic.Assistant.controller;

import com.internship.Academic.Assistant.service.QuestionAnswerService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp")
@CrossOrigin(origins = "*")
public class WhatsAppController {

    private final QuestionAnswerService questionAnswerService;

    public WhatsAppController(QuestionAnswerService questionAnswerService) {
        this.questionAnswerService = questionAnswerService;
    }

    @PostMapping
    public String receiveMessage(
            @RequestParam("Body") String message
    ) {

        System.out.println("WhatsApp message: " + message);

        String answer = questionAnswerService.answer(message);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                    <Message>%s</Message>
                </Response>
                """.formatted(answer);
    }
}