package com.finpilot.marketdata.service;

import com.finpilot.common.enums.AssetType;
import com.finpilot.marketdata.dto.MarketQuote;
import com.finpilot.marketdata.entity.MarketPriceCache;
import com.finpilot.marketdata.repository.MarketPriceCacheRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketCacheServiceImplTest {

    @Mock
    private MarketPriceCacheRepository cacheRepository;

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private MarketCacheServiceImpl marketCacheService;

    private MarketPriceCache cachedTcs(LocalDateTime lastUpdated) {
        return MarketPriceCache.builder()
                .symbol("TCS")
                .exchange("NSE")
                .assetType(AssetType.STOCK)
                .companyName("TCS")
                .currency("INR")
                .currentPrice(new BigDecimal("4000"))
                .lastUpdated(lastUpdated)
                .source("ALPHA_VANTAGE")
                .build();
    }

    @Test
    void lowercaseSymbolHitsUppercaseCacheEntry() {
        MarketPriceCache cached = cachedTcs(LocalDateTime.now());
        when(cacheRepository.findBySymbolAndExchange("TCS", "NSE")).thenReturn(Optional.of(cached));

        MarketPriceCache result = marketCacheService.getLatestMarketPrice(" tcs ", "NSE", AssetType.STOCK);

        assertThat(result).isSameAs(cached);
        verify(marketDataService, never()).fetchLatestPrice(anyString(), anyString(), any());
    }

    @Test
    void staleCacheIsReturnedWhenProviderFails() {
        MarketPriceCache stale = cachedTcs(LocalDateTime.now().minusHours(1));
        when(cacheRepository.findBySymbolAndExchange("TCS", "NSE")).thenReturn(Optional.of(stale));
        when(marketDataService.fetchLatestPrice("TCS", "NSE", AssetType.STOCK))
                .thenThrow(new RuntimeException("rate limited"));

        MarketPriceCache result = marketCacheService.getLatestMarketPrice("TCS", "NSE", AssetType.STOCK);

        assertThat(result).isSameAs(stale);
        assertThat(result.getCurrentPrice()).isEqualByComparingTo("4000");
    }

    @Test
    void missingCacheIsCreatedWithNormalizedSymbol() {
        when(cacheRepository.findBySymbolAndExchange("AAPL", "NASDAQ")).thenReturn(Optional.empty());
        when(marketDataService.fetchLatestPrice("AAPL", "NASDAQ", AssetType.STOCK))
                .thenReturn(MarketQuote.builder()
                        .symbol("AAPL")
                        .exchange("NASDAQ")
                        .assetType(AssetType.STOCK)
                        .currency("USD")
                        .currentPrice(new BigDecimal("190.50"))
                        .lastUpdated(LocalDateTime.now())
                        .source("ALPHA_VANTAGE")
                        .build());
        when(cacheRepository.save(any(MarketPriceCache.class))).thenAnswer(inv -> inv.getArgument(0));

        MarketPriceCache result = marketCacheService.getLatestMarketPrice("aapl", "Apple Inc", "NASDAQ", AssetType.STOCK);

        assertThat(result.getSymbol()).isEqualTo("AAPL");
        assertThat(result.getCompanyName()).isEqualTo("Apple Inc");
        assertThat(result.getCurrentPrice()).isEqualByComparingTo("190.50");
    }
}
