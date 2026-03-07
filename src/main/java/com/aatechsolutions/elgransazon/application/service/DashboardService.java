package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.presentation.dto.DashboardStatsDTO;

import java.util.List;

/**
 * Service interface for Dashboard statistics
 */
public interface DashboardService {

    /**
     * Get comprehensive dashboard statistics
     * 
     * @return Dashboard statistics DTO with all aggregated data
     */
    DashboardStatsDTO getDashboardStats();
    
    /**
     * Get popular items filtered by period
     * 
     * @param period Filter period: "today", "week", "month"
     * @return List of popular items for the specified period
     */
    List<DashboardStatsDTO.PopularItemDTO> getPopularItemsByPeriod(String period);
}
