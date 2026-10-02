package com.finpilot.insights.service;

import com.finpilot.common.enums.AlertSeverity;
import com.finpilot.common.enums.GoalPriority;
import com.finpilot.common.enums.GoalStatus;
import com.finpilot.goal.dto.GoalResponse;
import com.finpilot.goal.service.GoalService;
import com.finpilot.insights.client.OpenAiCompatibleChatClient;
import com.finpilot.insights.dto.AiPortfolioReview;
import com.finpilot.insights.dto.PortfolioInsights;
import com.finpilot.insights.exception.AiServiceUnavailableException;
import com.finpilot.risk.dto.RiskAlertResponse;
import com.finpilot.risk.service.RiskService;
import com.finpilot.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPortfolioReviewServiceTest {

    @Mock
    private PortfolioInsightsService insightsService;

    @Mock
    private GoalService goalService;

    @Mock
    private RiskService riskService;

    @Mock
    private OpenAiCompatibleChatClient chatClient;

    @InjectMocks
    private AiPortfolioReviewService reviewService;

    private final User user = User.builder().id(1L).build();

    private PortfolioInsights facts() {
        return PortfolioInsights.builder()
                .totalHoldings(3)
                .totalInvestment(new BigDecimal("91500"))
                .currentValue(new BigDecimal("100000"))
                .totalProfitLoss(new BigDecimal("8500"))
                .returnPercentage(new BigDecimal("9.29"))
                .largestHoldingSymbol("TCS")
                .largestHoldingName("Tata Consultancy Services")
                .largestHoldingValue(new BigDecimal("50000"))
                .largestHoldingPercentage(new BigDecimal("50.00"))
                .bestPerformerSymbol("TCS")
                .bestPerformerName("Tata Consultancy Services")
                .bestPerformerReturn(new BigDecimal("25.00"))
                .worstPerformerSymbol("119551")
                .worstPerformerName("HDFC Flexi Cap Fund")
                .worstPerformerReturn(new BigDecimal("-10.00"))
                .stockAllocationPercentage(new BigDecimal("50.00"))
                .etfAllocationPercentage(new BigDecimal("23.00"))
                .mutualFundAllocationPercentage(new BigDecimal("27.00"))
                .foreignCurrencyPercentage(new BigDecimal("23.00"))
                .diversificationScore(60)
                .build();
    }

    @Test
    void reviewSendsGroundedFactsGoalsAndAlertsToTheModel() {
        when(insightsService.generateInsights(user)).thenReturn(facts());
        when(goalService.getUserGoals(1L)).thenReturn(List.of(GoalResponse.builder()
                .goalName("House")
                .targetAmount(new BigDecimal("2000000"))
                .targetDate(LocalDate.of(2030, 1, 1))
                .priority(GoalPriority.HIGH)
                .status(GoalStatus.ACTIVE)
                .progressPercentage(new BigDecimal("5.00"))
                .build()));
        when(riskService.getRiskAlerts(1L)).thenReturn(List.of(RiskAlertResponse.builder()
                .title("Watchlist Empty").message("Not tracking any assets.").severity(AlertSeverity.LOW).build()));
        when(chatClient.complete(eq(AiPortfolioReviewService.SYSTEM_PROMPT), anyString())).thenReturn("Overview: ...");
        when(chatClient.getModel()).thenReturn("qwen2.5:3b");

        AiPortfolioReview review = reviewService.review(user);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatClient).complete(eq(AiPortfolioReviewService.SYSTEM_PROMPT), prompt.capture());
        assertThat(prompt.getValue())
                .contains("Current value: INR 100000")
                .contains("Largest holding: Tata Consultancy Services (symbol TCS), value INR 50000, 50.00% of the portfolio")
                .contains("Worst performer: HDFC Flexi Cap Fund (symbol 119551), return -10.00%")
                .contains("Foreign-currency (non-INR) exposure: 23.00%")
                .contains("House: target INR 2000000 by 2030-01-01, progress 5.00%")
                .contains("[LOW] Watchlist Empty");

        assertThat(review.getReview()).isEqualTo("Overview: ...");
        assertThat(review.getModel()).isEqualTo("qwen2.5:3b");
        assertThat(review.getDisclaimer()).isEqualTo(AiPortfolioReviewService.DISCLAIMER);
        assertThat(review.getFacts().getDiversificationScore()).isEqualTo(60);
    }

    @Test
    void systemPromptForbidsInventedNumbersAndTradeAdvice() {
        assertThat(AiPortfolioReviewService.SYSTEM_PROMPT)
                .contains("ONLY the facts provided")
                .contains("Never invent")
                .contains("Symbols are identifiers, never amounts")
                .contains("Do not recommend buying or selling");
    }

    @Test
    void emptyPortfolioSkipsTheModel() {
        when(insightsService.generateInsights(user)).thenReturn(PortfolioInsights.builder().totalHoldings(0).build());

        AiPortfolioReview review = reviewService.review(user);

        assertThat(review.getReview()).contains("portfolio is empty");
        verifyNoInteractions(chatClient, goalService, riskService);
    }

    @Test
    void modelFailurePropagatesAsAiServiceUnavailable() {
        when(insightsService.generateInsights(user)).thenReturn(facts());
        when(goalService.getUserGoals(1L)).thenReturn(List.of());
        when(riskService.getRiskAlerts(1L)).thenReturn(List.of());
        when(chatClient.complete(anyString(), anyString()))
                .thenThrow(new AiServiceUnavailableException("AI provider request failed"));

        assertThatThrownBy(() -> reviewService.review(user)).isInstanceOf(AiServiceUnavailableException.class);
    }
}
