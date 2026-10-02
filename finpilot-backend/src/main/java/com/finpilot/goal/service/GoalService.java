package com.finpilot.goal.service;

import com.finpilot.goal.dto.GoalRequest;
import com.finpilot.goal.dto.GoalResponse;

import java.util.List;
import java.util.UUID;

public interface GoalService {

    GoalResponse createGoal(Long userId, GoalRequest request);

    List<GoalResponse> getUserGoals(Long userId);

    GoalResponse updateGoal(Long userId, UUID goalId, GoalRequest request);

    void deleteGoal(Long userId, UUID goalId);

    GoalResponse getGoalById(Long userId, UUID goalId);

}
