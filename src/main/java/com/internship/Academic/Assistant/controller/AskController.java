package com.internship.Academic.Assistant.controller;

import com.internship.Academic.Assistant.service.QuestionAnswerService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AskController {

    private final QuestionAnswerService questionAnswerService;

    public AskController(QuestionAnswerService questionAnswerService) {
        this.questionAnswerService = questionAnswerService;
    }

    @PostMapping("/ask")
    public String ask(@RequestBody AskRequest request) {
        return questionAnswerService.answer(request.question());
    }

    public record AskRequest(String question) {
    }
}