package com.finpilot.insights.service;

import com.finpilot.common.enums.AssetType;
import com.finpilot.common.util.CurrencyConverter;
import com.finpilot.insights.dto.PortfolioInsights;
import com.finpilot.marketdata.entity.MarketPriceCache;
import com.finpilot.marketdata.service.MarketCacheService;
import com.finpilot.portfolio.entity.Portfolio;
import com.finpilot.portfolio.repository.PortfolioRepository;
import com.finpilot.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioInsightsServiceImpl implements PortfolioInsightsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PortfolioRepository portfolioRepository;
    private final MarketCacheService marketCacheService;
    private final CurrencyConverter currencyConverter;

    private record HoldingValue(
            String symbol,
            String name,
            AssetType assetType,
            boolean foreignCurrency,
            BigDecimal invested,
            BigDecimal current,
            BigDecimal returnPercentage
    ) {
    }

    @Override
    public PortfolioInsights generateInsights(User user) {

        List<HoldingValue> holdings = portfolioRepository.findByUserOrderByUpdatedAtDesc(user)
                .stream()
                .map(this::valueOf)
                .toList();

        if (holdings.isEmpty()) {
            return PortfolioInsights.builder()
                    .totalHoldings(0)
                    .totalInvestment(BigDecimal.ZERO)
                    .currentValue(BigDecimal.ZERO)
                    .totalProfitLoss(BigDecimal.ZERO)
                    .returnPercentage(BigDecimal.ZERO)
                    .diversificationScore(0)
                    .build();
        }

        BigDecimal totalInvestment = sum(holdings.stream().map(HoldingValue::invested).toList());
        BigDecimal currentValue = sum(holdings.stream().map(HoldingValue::current).toList());
        BigDecimal profitLoss = currentValue.subtract(totalInvestment);

        HoldingValue largest = holdings.stream().max(Comparator.comparing(HoldingValue::current)).orElseThrow();
        HoldingValue best = holdings.stream().max(Comparator.comparing(HoldingValue::returnPercentage)).orElseThrow();
        HoldingValue worst = holdings.stream().min(Comparator.comparing(HoldingValue::returnPercentage)).orElseThrow();

        BigDecimal foreignValue = sum(holdings.stream()
                .filter(HoldingValue::foreignCurrency)
                .map(HoldingValue::current)
                .toList());

        return PortfolioInsights.builder()
                .totalHoldings(holdings.size())
                .totalInvestment(totalInvestment)
                .currentValue(currentValue)
                .totalProfitLoss(profitLoss)
                .returnPercentage(percentage(profitLoss, totalInvestment))
                .largestHoldingSymbol(largest.symbol())
                .largestHoldingName(largest.name())
                .largestHoldingValue(largest.current())
                .largestHoldingPercentage(percentage(largest.current(), currentValue))
                .bestPerformerSymbol(best.symbol())
                .bestPerformerName(best.name())
                .bestPerformerReturn(best.returnPercentage())
                .worstPerformerSymbol(worst.symbol())
                .worstPerformerName(worst.name())
                .worstPerformerReturn(worst.returnPercentage())
                .diversificationScore(diversificationScore(holdings, currentValue))
                .stockAllocationPercentage(percentage(valueOfType(holdings, AssetType.STOCK), currentValue))
                .mutualFundAllocationPercentage(percentage(valueOfType(holdings, AssetType.MUTUAL_FUND), currentValue))
                .etfAllocationPercentage(percentage(valueOfType(holdings, AssetType.ETF), currentValue))
                .foreignCurrencyPercentage(percentage(foreignValue, currentValue))
                .build();
    }

    private HoldingValue valueOf(Portfolio portfolio) {

        MarketPriceCache marketPrice = marketCacheService.getLatestMarketPrice(
                portfolio.getAssetSymbol(),
                portfolio.getExchange(),
                portfolio.getAssetType()
        );

        String currency = marketPrice.getCurrency();

        BigDecimal invested = currencyConverter.convertToINR(
                portfolio.getPurchasePrice().multiply(portfolio.getQuantity()), currency);
        BigDecimal current = currencyConverter.convertToINR(
                marketPrice.getCurrentPrice().multiply(portfolio.getQuantity()), currency);

        return new HoldingValue(
                portfolio.getAssetSymbol(),
                portfolio.getAssetName(),
                portfolio.getAssetType(),
                currency != null && !currency.equalsIgnoreCase("INR"),
                invested,
                current,
                percentage(current.subtract(invested), invested)
        );
    }

    /**
     * 0-100 score from the Herfindahl-Hirschman index of holding weights:
     * one holding scores 0, n equally weighted holdings score 100 * (1 - 1/n).
     */
    private int diversificationScore(List<HoldingValue> holdings, BigDecimal currentValue) {

        if (currentValue.signum() <= 0) {
            return 0;
        }

        double hhi = holdings.stream()
                .mapToDouble(h -> h.current().divide(currentValue, 10, RoundingMode.HALF_UP).doubleValue())
                .map(weight -> weight * weight)
                .sum();

        return (int) Math.round((1 - hhi) * 100);
    }

    private BigDecimal valueOfType(List<HoldingValue> holdings, AssetType type) {
        return sum(holdings.stream().filter(h -> h.assetType() == type).map(HoldingValue::current).toList());
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal percentage(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
