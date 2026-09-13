package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Represents a thermal printer configured per company for comanda printing.
 * Each company can have at most ONE printer per PrinterType (KITCHEN, BAR, PARRILLERO).
 * The same Windows printer name may be assigned to more than one type.
 *
 * The printer name must exactly match the Windows printer name so QZ Tray can find it
 * via qz.printers.find(name) on the agent PC.
 */
@Entity
@Table(
    name = "printers",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_printer_company_type", columnNames = {"company_id", "printer_type"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "company")
public class Printer implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ── Multi-tenant ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // ── Printer name (must match Windows printer name exactly) ──
    @NotBlank(message = "El nombre de la impresora es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // ── Comanda type this printer handles ──
    @NotNull(message = "El tipo de comanda es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "printer_type", nullable = false, length = 20)
    private PrinterType printerType;

    // ── Optional IP address for Wi-Fi printers ──
    @Pattern(
        regexp = "^$|^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$",
        message = "Formato de IP inválido (ej. 192.168.1.100)"
    )
    @Size(max = 50, message = "La IP no puede exceder 50 caracteres")
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    // ── Timestamps ──
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
