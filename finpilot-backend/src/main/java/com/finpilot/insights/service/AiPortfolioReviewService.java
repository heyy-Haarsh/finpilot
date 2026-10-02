package com.finpilot.insights.service;

import com.finpilot.common.cache.CacheConfig;
import com.finpilot.goal.dto.GoalResponse;
import com.finpilot.goal.service.GoalService;
import com.finpilot.insights.client.OpenAiCompatibleChatClient;
import com.finpilot.insights.dto.AiPortfolioReview;
import com.finpilot.insights.dto.PortfolioInsights;
import com.finpilot.risk.dto.RiskAlertResponse;
import com.finpilot.risk.service.RiskService;
import com.finpilot.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiPortfolioReviewService {

    static final String DISCLAIMER =
            "AI-generated explanation of your own portfolio data. Not financial advice.";

    static final String SYSTEM_PROMPT = """
            You are the portfolio review assistant inside FinPilot, a personal investment tracker.
            You explain the user's own portfolio using ONLY the facts provided.
            Rules:
            - Never invent holdings, prices, percentages or any other figures.
            - Do not calculate new numbers; quote numbers exactly as given.
            - Do not calculate new numbers; quote numbers exactly as given.
            - Symbols are identifiers, never amounts: refer to holdings by name.
            - Do not recommend buying or selling any specific security.
            - All amounts are in INR.
            - Write three short sections with these headings: Overview, Risks, Goals.
            - Keep the whole review under 180 words, in plain language.
            """;

    private final PortfolioInsightsService insightsService;
    private final GoalService goalService;
    private final RiskService riskService;
    private final OpenAiCompatibleChatClient chatClient;

    @Cacheable(cacheNames = CacheConfig.AI_REVIEWS, key = "#user.id")
    public AiPortfolioReview review(User user) {

        PortfolioInsights facts = insightsService.generateInsights(user);

        if (facts.getTotalHoldings() == 0) {
            return AiPortfolioReview.builder()
                    .review("Your portfolio is empty. Add some holdings to get an AI review.")
                    .generatedAt(LocalDateTime.now())
                    .disclaimer(DISCLAIMER)
                    .facts(facts)
                    .build();
        }

        List<GoalResponse> goals = goalService.getUserGoals(user.getId());
        List<RiskAlertResponse> alerts = riskService.getRiskAlerts(user.getId());

        String review = chatClient.complete(SYSTEM_PROMPT, buildPrompt(facts, goals, alerts));

        return AiPortfolioReview.builder()
                .review(review)
                .model(chatClient.getModel())
                .generatedAt(LocalDateTime.now())
                .disclaimer(DISCLAIMER)
                .facts(facts)
                .build();
    }

    String buildPrompt(PortfolioInsights facts, List<GoalResponse> goals, List<RiskAlertResponse> alerts) {

        StringBuilder prompt = new StringBuilder("Portfolio facts:\n")
                .append("- Number of holdings: ").append(facts.getTotalHoldings()).append('\n')
                .append("- Total invested: INR ").append(facts.getTotalInvestment()).append('\n')
                .append("- Current value: INR ").append(facts.getCurrentValue()).append('\n')
                .append("- Profit/loss: INR ").append(facts.getTotalProfitLoss())
                .append(", return ").append(facts.getReturnPercentage()).append("%\n")
                .append("- Largest holding: ").append(holding(facts.getLargestHoldingName(), facts.getLargestHoldingSymbol()))
                .append(", value INR ").append(facts.getLargestHoldingValue())
                .append(", ").append(facts.getLargestHoldingPercentage()).append("% of the portfolio\n")
                .append("- Best performer: ").append(holding(facts.getBestPerformerName(), facts.getBestPerformerSymbol()))
                .append(", return ").append(facts.getBestPerformerReturn()).append("%\n")
                .append("- Worst performer: ").append(holding(facts.getWorstPerformerName(), facts.getWorstPerformerSymbol()))
                .append(", return ").append(facts.getWorstPerformerReturn()).append("%\n")
                .append("- Allocation: stocks ").append(facts.getStockAllocationPercentage())
                .append("%, ETFs ").append(facts.getEtfAllocationPercentage())
                .append("%, mutual funds ").append(facts.getMutualFundAllocationPercentage()).append("%\n")
                .append("- Foreign-currency (non-INR) exposure: ").append(facts.getForeignCurrencyPercentage()).append("%\n")
                .append("- Diversification score (0-100): ").append(facts.getDiversificationScore()).append('\n');

        prompt.append("\nGoals:\n");
        if (goals.isEmpty()) {
            prompt.append("- None\n");
        }
        goals.forEach(goal -> prompt.append("- ").append(goal.getGoalName())
                .append(": target INR ").append(goal.getTargetAmount())
                .append(" by ").append(goal.getTargetDate())
                .append(", progress ").append(goal.getProgressPercentage()).append("%")
                .append(", status ").append(goal.getStatus()).append('\n'));

        prompt.append("\nRisk alerts:\n");
        alerts.forEach(alert -> prompt.append("- [").append(alert.getSeverity()).append("] ")
                .append(alert.getTitle()).append(": ").append(alert.getMessage()).append('\n'));

        return prompt.toString();
    }

    private static String holding(String name, String symbol) {
        return name == null || name.equalsIgnoreCase(symbol)
                ? "symbol " + symbol
                : name + " (symbol " + symbol + ")";
    }
}
