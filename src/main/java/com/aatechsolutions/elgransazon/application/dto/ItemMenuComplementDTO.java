package com.aatechsolutions.elgransazon.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for ItemMenuComplement - configuration of complements available for an item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemMenuComplementDTO {
    
    private Long id;
    private Long itemMenuId;
    private String itemMenuName;
    private Long complementId;
    private String complementName;
    private BigDecimal complementExtraPrice;
    private Integer maxQuantity;
    private Integer displayOrder;
    private Boolean active;
    private Boolean available;
    private Boolean hasEnoughStock;
    private Boolean isSauce;
    private Boolean isSpeciality;
}
