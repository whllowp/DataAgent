package com.dataagent.web;

import com.dataagent.service.FeedbackService;
import com.dataagent.web.dto.FeedbackDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbacks;

    public FeedbackController(FeedbackService feedbacks) {
        this.feedbacks = feedbacks;
    }

    @GetMapping
    public List<FeedbackDtos.View> recent() {
        return feedbacks.recent();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackDtos.Result submit(@Valid @RequestBody FeedbackDtos.Request request) {
        return feedbacks.submit(request);
    }
}
