package com.internship.Academic.Assistant.controller;

import com.internship.Academic.Assistant.service.QuestionAnswerService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class QuestionAnswerController {

    private final QuestionAnswerService questionAnswerService;

    public QuestionAnswerController(QuestionAnswerService questionAnswerService) {
        this.questionAnswerService = questionAnswerService;
    }

    @PostMapping("/question")
    public String ask(@RequestBody QuestionRequest request) {
        return questionAnswerService.answer(request.question());
    }

    public record QuestionRequest(String question) {}
}