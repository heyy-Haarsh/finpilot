package com.finpilot.insights.controller;

import com.finpilot.auth.service.CustomUserDetails;
import com.finpilot.insights.dto.AiPortfolioReview;
import com.finpilot.insights.dto.PortfolioInsights;
import com.finpilot.insights.service.AiPortfolioReviewService;
import com.finpilot.insights.service.PortfolioInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final PortfolioInsightsService insightsService;
    private final AiPortfolioReviewService aiPortfolioReviewService;

    @GetMapping
    public ResponseEntity<PortfolioInsights> getInsights(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(insightsService.generateInsights(userDetails.getUser()));
    }

    @GetMapping("/ai-review")
    public ResponseEntity<AiPortfolioReview> getAiReview(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(aiPortfolioReviewService.review(userDetails.getUser()));
    }
}
