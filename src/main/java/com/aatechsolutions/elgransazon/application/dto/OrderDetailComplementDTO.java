package com.aatechsolutions.elgransazon.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for OrderDetailComplement - selected complement for an order detail
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailComplementDTO {
    
    private Long id;
    private Long orderDetailId;
    private Long complementId;
    private String complementName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String notes;
}
