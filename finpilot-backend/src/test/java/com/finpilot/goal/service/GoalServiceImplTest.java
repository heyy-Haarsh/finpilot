package com.finpilot.goal.service;

import com.finpilot.common.enums.GoalPriority;
import com.finpilot.common.enums.GoalStatus;
import com.finpilot.common.exception.ResourceNotFoundException;
import com.finpilot.goal.dto.GoalRequest;
import com.finpilot.goal.dto.GoalResponse;
import com.finpilot.goal.entity.Goal;
import com.finpilot.goal.repository.GoalRepository;
import com.finpilot.portfolio.dto.PortfolioSummaryResponse;
import com.finpilot.portfolio.service.PortfolioAnalyticsService;
import com.finpilot.user.entity.User;
import com.finpilot.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalServiceImplTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortfolioAnalyticsService portfolioAnalyticsService;

    @InjectMocks
    private GoalServiceImpl goalService;

    private User user;
    private UUID goalId;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("user@example.com").build();
        goalId = UUID.randomUUID();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private Goal existingGoal(User owner, BigDecimal target, GoalStatus status) {
        return Goal.builder()
                .id(goalId)
                .user(owner)
                .goalName("Old name")
                .targetAmount(target)
                .targetDate(LocalDate.now().plusYears(1))
                .priority(GoalPriority.LOW)
                .status(status)
                .build();
    }

    private GoalRequest request(BigDecimal target) {
        return GoalRequest.builder()
                .goalName("House")
                .targetAmount(target)
                .targetDate(LocalDate.now().plusYears(5))
                .priority(GoalPriority.HIGH)
                .build();
    }

    private void portfolioValueIs(String value) {
        when(portfolioAnalyticsService.getPortfolioSummary(user))
                .thenReturn(PortfolioSummaryResponse.builder().currentValue(new BigDecimal(value)).build());
    }

    @Test
    void updateGoalAppliesAllFieldsAndReturnsProgress() {
        Goal goal = existingGoal(user, new BigDecimal("1000"), GoalStatus.ACTIVE);
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));
        portfolioValueIs("25000");

        GoalRequest request = request(new BigDecimal("100000"));
        GoalResponse response = goalService.updateGoal(1L, goalId, request);

        assertThat(response.getGoalName()).isEqualTo("House");
        assertThat(response.getTargetAmount()).isEqualByComparingTo("100000");
        assertThat(response.getTargetDate()).isEqualTo(request.getTargetDate());
        assertThat(response.getPriority()).isEqualTo(GoalPriority.HIGH);
        assertThat(response.getStatus()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(response.getProgressPercentage()).isEqualByComparingTo("25.00");
        assertThat(response.getRemainingAmount()).isEqualByComparingTo("75000");
    }

    @Test
    void raisingTargetReopensCompletedGoal() {
        Goal goal = existingGoal(user, new BigDecimal("1000"), GoalStatus.COMPLETED);
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));
        portfolioValueIs("5000");

        GoalResponse response = goalService.updateGoal(1L, goalId, request(new BigDecimal("10000")));

        assertThat(response.getStatus()).isEqualTo(GoalStatus.ACTIVE);
    }

    @Test
    void loweringTargetBelowPortfolioValueCompletesGoal() {
        Goal goal = existingGoal(user, new BigDecimal("100000"), GoalStatus.ACTIVE);
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));
        portfolioValueIs("5000");

        GoalResponse response = goalService.updateGoal(1L, goalId, request(new BigDecimal("4000")));

        assertThat(response.getStatus()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(response.getProgressPercentage()).isEqualByComparingTo("100");
    }

    @Test
    void updatingAnotherUsersGoalReturnsNotFound() {
        User otherUser = User.builder().id(2L).build();
        when(goalRepository.findById(goalId))
                .thenReturn(Optional.of(existingGoal(otherUser, new BigDecimal("1000"), GoalStatus.ACTIVE)));

        assertThatThrownBy(() -> goalService.updateGoal(1L, goalId, request(new BigDecimal("2000"))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Goal not found");

        verify(goalRepository, never()).save(any());
    }

    @Test
    void updatingMissingGoalReturnsNotFound() {
        when(goalRepository.findById(goalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.updateGoal(1L, goalId, request(new BigDecimal("2000"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
