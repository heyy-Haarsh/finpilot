package com.finpilot.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPortfolioReview {

    private String review;

    private String model;

    private LocalDateTime generatedAt;

    private String disclaimer;

    private PortfolioInsights facts;
}
