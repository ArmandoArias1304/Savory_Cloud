package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SocialNetwork entity representing social media links for the restaurant
 */
@Entity
@Table(name = "social_networks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"systemConfiguration"})
public class SocialNetwork implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotBlank(message = "El nombre de la red social es obligatorio")
    @Size(min = 2, max = 50, message = "El nombre de la red social debe tener entre 2 y 50 caracteres")
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @NotBlank(message = "La URL de la red social es obligatoria")
    @Pattern(regexp = "^https?://.*", message = "La URL debe comenzar con http:// o https://")
    @Size(max = 500, message = "La URL no puede exceder los 500 caracteres")
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Size(max = 100, message = "El icono no puede exceder los 100 caracteres")
    @Column(name = "icon", length = 100)
    private String icon;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_configuration_id", nullable = false)
    private SystemConfiguration systemConfiguration;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
