package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Records that an employee already saw a system update, so the automatic
 * modal is shown only once per version and employee.
 */
@Entity
@Table(name = "system_update_views", uniqueConstraints = {
    @UniqueConstraint(name = "uk_system_update_view_employee",
            columnNames = {"update_id", "employee_id"})
}, indexes = {
    @Index(name = "idx_system_update_view_employee", columnList = "employee_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString
public class SystemUpdateView implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "update_id", nullable = false)
    private Long updateId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "viewed_at", nullable = false)
    @Builder.Default
    private LocalDateTime viewedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.viewedAt == null) {
            this.viewedAt = LocalDateTime.now();
        }
    }
}
