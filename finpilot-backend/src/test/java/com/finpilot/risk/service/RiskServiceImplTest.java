package com.finpilot.risk.service;

import com.finpilot.common.enums.AlertSeverity;
import com.finpilot.dashboard.dto.DashboardResponse;
import com.finpilot.dashboard.service.DashboardService;
import com.finpilot.risk.dto.RiskAlertResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskServiceImplTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private RiskServiceImpl riskService;

    private DashboardResponse.DashboardResponseBuilder healthyDashboard() {
        return DashboardResponse.builder()
                .totalAssets(5L)
                .totalProfitLoss(new BigDecimal("1000"))
                .activeGoals(1L)
                .completedGoals(0L)
                .watchlistCount(3L);
    }

    private List<String> titles(List<RiskAlertResponse> alerts) {
        return alerts.stream().map(RiskAlertResponse::getTitle).toList();
    }

    @Test
    void healthyPortfolioGetsSingleSuccessAlert() {
        when(dashboardService.getDashboard(1L)).thenReturn(healthyDashboard().build());

        List<RiskAlertResponse> alerts = riskService.getRiskAlerts(1L);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getSeverity()).isEqualTo(AlertSeverity.SUCCESS);
    }

    @Test
    void newUserGetsAlertsSortedBySeverity() {
        when(dashboardService.getDashboard(1L)).thenReturn(healthyDashboard()
                .totalAssets(0L)
                .totalProfitLoss(BigDecimal.ZERO)
                .activeGoals(0L)
                .watchlistCount(0L)
                .build());

        List<RiskAlertResponse> alerts = riskService.getRiskAlerts(1L);

        assertThat(titles(alerts)).containsExactly("Empty Portfolio", "No Active Goals", "Watchlist Empty");
        assertThat(alerts).extracting(RiskAlertResponse::getSeverity)
                .containsExactly(AlertSeverity.HIGH, AlertSeverity.MEDIUM, AlertSeverity.LOW);
    }

    @Test
    void lossAndCompletedGoalProduceTheirAlerts() {
        when(dashboardService.getDashboard(1L)).thenReturn(healthyDashboard()
                .totalProfitLoss(new BigDecimal("-500"))
                .completedGoals(1L)
                .build());

        List<RiskAlertResponse> alerts = riskService.getRiskAlerts(1L);

        assertThat(titles(alerts)).containsExactly("Portfolio Loss", "Goal Achieved");
    }
}
