package com.finpilot.insights.service;

import com.finpilot.insights.dto.PortfolioInsights;
import com.finpilot.user.entity.User;

public interface PortfolioInsightsService {

    PortfolioInsights generateInsights(User user);

}
