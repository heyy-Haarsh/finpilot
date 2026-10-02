package com.finpilot.insights.service;

import com.finpilot.common.enums.AssetType;
import com.finpilot.common.util.CurrencyConverter;
import com.finpilot.insights.dto.PortfolioInsights;
import com.finpilot.marketdata.entity.MarketPriceCache;
import com.finpilot.marketdata.service.MarketCacheService;
import com.finpilot.portfolio.entity.Portfolio;
import com.finpilot.portfolio.repository.PortfolioRepository;
import com.finpilot.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioInsightsServiceImplTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private MarketCacheService marketCacheService;

    private PortfolioInsightsServiceImpl insightsService;

    private final User user = User.builder().id(1L).build();

    @BeforeEach
    void setUp() {
        insightsService = new PortfolioInsightsServiceImpl(portfolioRepository, marketCacheService, new CurrencyConverter());
    }

    private Portfolio holding(String symbol, String exchange, AssetType type, String qty, String buyPrice) {
        return Portfolio.builder()
                .user(user)
                .assetSymbol(symbol)
                .assetName(symbol + " name")
                .exchange(exchange)
                .assetType(type)
                .quantity(new BigDecimal(qty))
                .purchasePrice(new BigDecimal(buyPrice))
                .build();
    }

    private void price(String symbol, String exchange, AssetType type, String price, String currency) {
        when(marketCacheService.getLatestMarketPrice(symbol, exchange, type))
                .thenReturn(MarketPriceCache.builder().currentPrice(new BigDecimal(price)).currency(currency).build());
    }

    @Test
    void computesConcentrationPerformanceAllocationAndCurrencyExposure() {
        when(portfolioRepository.findByUserOrderByUpdatedAtDesc(user)).thenReturn(List.of(
                holding("TCS", "NSE", AssetType.STOCK, "10", "4000"),        // 40,000 -> 50,000 (+25%)
                holding("119551", "MF", AssetType.MUTUAL_FUND, "100", "300"), // 30,000 -> 27,000 (-10%)
                holding("VOO", "NYSE", AssetType.ETF, "1", "250")             // 21,500 -> 23,000 INR (+6.98%)
        ));
        price("TCS", "NSE", AssetType.STOCK, "5000", "INR");
        price("119551", "MF", AssetType.MUTUAL_FUND, "270", "INR");
        price("VOO", "NYSE", AssetType.ETF, "267.4418604651", "USD");

        PortfolioInsights insights = insightsService.generateInsights(user);

        assertThat(insights.getTotalHoldings()).isEqualTo(3);
        assertThat(insights.getTotalInvestment()).isEqualByComparingTo("91500");
        assertThat(insights.getCurrentValue()).isCloseTo(new BigDecimal("100000"), within(new BigDecimal("0.01")));

        assertThat(insights.getLargestHoldingSymbol()).isEqualTo("TCS");
        assertThat(insights.getLargestHoldingPercentage()).isEqualByComparingTo("50.00");
        assertThat(insights.getBestPerformerSymbol()).isEqualTo("TCS");
        assertThat(insights.getBestPerformerReturn()).isEqualByComparingTo("25.00");
        assertThat(insights.getWorstPerformerSymbol()).isEqualTo("119551");
        assertThat(insights.getWorstPerformerReturn()).isEqualByComparingTo("-10.00");

        assertThat(insights.getStockAllocationPercentage()).isEqualByComparingTo("50.00");
        assertThat(insights.getMutualFundAllocationPercentage()).isEqualByComparingTo("27.00");
        assertThat(insights.getEtfAllocationPercentage()).isEqualByComparingTo("23.00");
        assertThat(insights.getForeignCurrencyPercentage()).isEqualByComparingTo("23.00");

        // 1 - (0.50^2 + 0.27^2 + 0.23^2) = 0.6242 -> 62
        assertThat(insights.getDiversificationScore()).isEqualTo(62);
    }

    @Test
    void singleHoldingHasZeroDiversification() {
        when(portfolioRepository.findByUserOrderByUpdatedAtDesc(user))
                .thenReturn(List.of(holding("TCS", "NSE", AssetType.STOCK, "1", "100")));
        price("TCS", "NSE", AssetType.STOCK, "100", "INR");

        PortfolioInsights insights = insightsService.generateInsights(user);

        assertThat(insights.getDiversificationScore()).isZero();
        assertThat(insights.getLargestHoldingPercentage()).isEqualByComparingTo("100.00");
        assertThat(insights.getForeignCurrencyPercentage()).isEqualByComparingTo("0");
    }

    @Test
    void emptyPortfolioReturnsZeroedInsights() {
        when(portfolioRepository.findByUserOrderByUpdatedAtDesc(user)).thenReturn(List.of());

        PortfolioInsights insights = insightsService.generateInsights(user);

        assertThat(insights.getTotalHoldings()).isZero();
        assertThat(insights.getCurrentValue()).isEqualByComparingTo("0");
        assertThat(insights.getLargestHoldingSymbol()).isNull();
    }
}
