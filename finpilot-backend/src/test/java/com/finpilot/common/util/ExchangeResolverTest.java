package com.finpilot.common.util;

import com.finpilot.common.enums.AssetType;
import com.finpilot.portfolio.exception.InvalidExchangeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeResolverTest {

    private final ExchangeResolver resolver = new ExchangeResolver();

    @Test
    void mutualFundsAlwaysUseMfExchange() {
        assertThat(resolver.resolve(null, AssetType.MUTUAL_FUND)).isEqualTo("MF");
        assertThat(resolver.resolve("NSE", AssetType.MUTUAL_FUND)).isEqualTo("MF");
    }

    @Test
    void stockExchangeIsTrimmedAndUppercased() {
        assertThat(resolver.resolve(" nse ", AssetType.STOCK)).isEqualTo("NSE");
        assertThat(resolver.resolve("nasdaq", AssetType.ETF)).isEqualTo("NASDAQ");
    }

    @Test
    void stockWithoutExchangeIsRejected() {
        assertThatThrownBy(() -> resolver.resolve(null, AssetType.STOCK))
                .isInstanceOf(InvalidExchangeException.class);
        assertThatThrownBy(() -> resolver.resolve("  ", AssetType.ETF))
                .isInstanceOf(InvalidExchangeException.class);
    }
}
