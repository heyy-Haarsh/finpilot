package com.finpilot.portfolio.service;

import com.finpilot.common.enums.AssetType;
import com.finpilot.common.exception.ResourceNotFoundException;
import com.finpilot.common.util.ExchangeResolver;
import com.finpilot.marketdata.entity.MarketPriceCache;
import com.finpilot.marketdata.service.MarketCacheService;
import com.finpilot.portfolio.dto.PortfolioRequest;
import com.finpilot.portfolio.dto.PortfolioResponse;
import com.finpilot.portfolio.entity.Portfolio;
import com.finpilot.portfolio.repository.PortfolioRepository;
import com.finpilot.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private MarketCacheService marketCacheService;

    private PortfolioServiceImpl portfolioService;

    private final User user = User.builder().id(1L).build();

    @BeforeEach
    void setUp() {
        portfolioService = new PortfolioServiceImpl(portfolioRepository, marketCacheService, new ExchangeResolver());
    }

    private PortfolioRequest request(String symbol, String quantity, String price) {
        return PortfolioRequest.builder()
                .assetSymbol(symbol)
                .assetName("Tata Consultancy Services")
                .assetType(AssetType.STOCK)
                .exchange("nse")
                .quantity(new BigDecimal(quantity))
                .purchasePrice(new BigDecimal(price))
                .purchaseDate(LocalDate.now().minusDays(1))
                .build();
    }

    private void marketPriceIs(String price) {
        when(marketCacheService.getLatestMarketPrice(eq("TCS"), anyString(), eq("NSE"), eq(AssetType.STOCK)))
                .thenReturn(MarketPriceCache.builder().currentPrice(new BigDecimal(price)).currency("INR").build());
    }

    @Test
    void addingNewAssetStoresNormalizedSymbolAndExchange() {
        marketPriceIs("4000");
        when(portfolioRepository.findByUserAndAssetSymbolAndExchangeAndAssetType(user, "TCS", "NSE", AssetType.STOCK))
                .thenReturn(Optional.empty());
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

        PortfolioResponse response = portfolioService.addAsset(user, request("tcs", "10", "3500"));

        assertThat(response.getAssetSymbol()).isEqualTo("TCS");
        assertThat(response.getExchange()).isEqualTo("NSE");
        assertThat(response.getQuantity()).isEqualByComparingTo("10");
        assertThat(response.getCurrentPrice()).isEqualByComparingTo("4000");
    }

    @Test
    void buyingMoreOfExistingAssetAveragesPurchasePrice() {
        marketPriceIs("4000");
        Portfolio existing = Portfolio.builder()
                .user(user)
                .assetSymbol("TCS")
                .exchange("NSE")
                .assetType(AssetType.STOCK)
                .quantity(new BigDecimal("10"))
                .purchasePrice(new BigDecimal("100"))
                .build();
        when(portfolioRepository.findByUserAndAssetSymbolAndExchangeAndAssetType(user, "TCS", "NSE", AssetType.STOCK))
                .thenReturn(Optional.of(existing));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

        PortfolioResponse response = portfolioService.addAsset(user, request("TCS", "30", "200"));

        // (10 * 100 + 30 * 200) / 40 = 175
        assertThat(response.getQuantity()).isEqualByComparingTo("40");
        assertThat(response.getPurchasePrice()).isEqualByComparingTo("175");
        assertThat(response.getCurrentPrice()).isEqualByComparingTo("4000");
    }

    @Test
    void updateReplacesFieldsOfOwnedAsset() {
        UUID id = UUID.randomUUID();
        marketPriceIs("4100");
        Portfolio existing = Portfolio.builder().id(id).user(user).build();
        when(portfolioRepository.findByIdAndUser(id, user)).thenReturn(Optional.of(existing));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

        portfolioService.updateAsset(user, id, request("tcs", "5", "3900"));

        ArgumentCaptor<Portfolio> saved = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(saved.capture());
        assertThat(saved.getValue().getAssetSymbol()).isEqualTo("TCS");
        assertThat(saved.getValue().getExchange()).isEqualTo("NSE");
        assertThat(saved.getValue().getQuantity()).isEqualByComparingTo("5");
        assertThat(saved.getValue().getCurrentPrice()).isEqualByComparingTo("4100");
    }

    @Test
    void updatingAssetNotOwnedByUserReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUser(id, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.updateAsset(user, id, request("TCS", "5", "3900")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void deletingAssetNotOwnedByUserReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUser(id, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.deleteAsset(user, id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(portfolioRepository, never()).delete(any());
    }
}
