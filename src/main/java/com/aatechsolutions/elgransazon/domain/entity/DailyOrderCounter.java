package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "daily_order_counters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyOrderCounter {

    @EmbeddedId
    private DailyOrderCounterKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("companyId")
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "last_sequence", nullable = false)
    private Integer lastSequence;
    
    // Convenience constructor
    public DailyOrderCounter(LocalDate date, Company company, Integer lastSequence) {
        this.id = new DailyOrderCounterKey(date, company.getIdCompany());
        this.company = company;
        this.lastSequence = lastSequence;
    }
    
    // Convenience getter for date
    public LocalDate getDate() {
        return id != null ? id.getDate() : null;
    }
}
