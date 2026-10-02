package com.finpilot.portfolio.service;

import com.finpilot.common.enums.AssetType;
import com.finpilot.common.util.CurrencyConverter;
import com.finpilot.marketdata.entity.MarketPriceCache;
import com.finpilot.marketdata.service.MarketCacheService;
import com.finpilot.portfolio.dto.PortfolioAllocationResponse;
import com.finpilot.portfolio.dto.PortfolioSummaryResponse;
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
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalyticsServiceImplTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private MarketCacheService marketCacheService;

    private PortfolioAnalyticsServiceImpl analyticsService;

    private final User user = User.builder().id(1L).build();

    @BeforeEach
    void setUp() {
        analyticsService = new PortfolioAnalyticsServiceImpl(portfolioRepository, marketCacheService, new CurrencyConverter());
    }

    private Portfolio holding(String symbol, String exchange, AssetType type, String qty, String buyPrice) {
        return Portfolio.builder()
                .user(user)
                .assetSymbol(symbol)
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

    private void givenInrStockAndUsdEtf() {
        when(portfolioRepository.findByUserOrderByUpdatedAtDesc(user)).thenReturn(List.of(
                holding("TCS", "NSE", AssetType.STOCK, "10", "3000"),
                holding("VOO", "NYSE", AssetType.ETF, "2", "100")
        ));
        price("TCS", "NSE", AssetType.STOCK, "3500", "INR");
        price("VOO", "NYSE", AssetType.ETF, "150", "USD");
    }

    @Test
    void summaryConvertsUsdToInrAndComputesReturn() {
        givenInrStockAndUsdEtf();

        PortfolioSummaryResponse summary = analyticsService.getPortfolioSummary(user);

        // Investment: 30,000 + (200 USD * 86) = 47,200. Current: 35,000 + (300 USD * 86) = 60,800.
        assertThat(summary.getTotalInvestment()).isEqualByComparingTo("47200");
        assertThat(summary.getCurrentValue()).isEqualByComparingTo("60800");
        assertThat(summary.getTotalProfitLoss()).isEqualByComparingTo("13600");
        assertThat(summary.getReturnPercentage()).isEqualByComparingTo("28.81");
        assertThat(summary.getAssetCount()).isEqualTo(2);
    }

    @Test
    void emptyPortfolioHasZeroReturnInsteadOfDivisionByZero() {
        when(portfolioRepository.findByUserOrderByUpdatedAtDesc(user)).thenReturn(List.of());

        PortfolioSummaryResponse summary = analyticsService.getPortfolioSummary(user);

        assertThat(summary.getTotalInvestment()).isEqualByComparingTo("0");
        assertThat(summary.getReturnPercentage()).isEqualByComparingTo("0");
        assertThat(summary.getAssetCount()).isZero();
    }

    @Test
    void allocationSplitsCurrentValueByAssetType() {
        givenInrStockAndUsdEtf();

        Map<AssetType, Double> percentages = analyticsService.getPortfolioAllocation(user).stream()
                .collect(Collectors.toMap(PortfolioAllocationResponse::getAssetType, PortfolioAllocationResponse::getPercentage));

        // 35,000 / 60,800 and 25,800 / 60,800
        assertThat(percentages).containsEntry(AssetType.STOCK, 57.57).containsEntry(AssetType.ETF, 42.43);
    }
}
