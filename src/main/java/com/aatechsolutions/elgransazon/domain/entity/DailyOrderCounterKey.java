package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Composite primary key for DailyOrderCounter (date + company)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class DailyOrderCounterKey implements Serializable {
    @Column(name = "counter_date")
    private LocalDate date;
    
    @Column(name = "company_id")
    private Long companyId;
}
