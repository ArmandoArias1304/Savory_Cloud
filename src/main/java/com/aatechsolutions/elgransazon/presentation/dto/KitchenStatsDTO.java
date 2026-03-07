package com.aatechsolutions.elgransazon.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for kitchen statistics WebSocket updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenStatsDTO {
    private Integer pendingCount;
    private Integer inPreparationCount;
    private Integer activeChefsCount;
    private Double avgPreparationTime;
}
