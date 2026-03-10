package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "landing_images",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_landing_image_company_section_position",
           columnNames = {"company_id", "section", "position"}
       ),
       indexes = {
           @Index(name = "idx_landing_image_company", columnList = "company_id"),
           @Index(name = "idx_landing_image_company_section", columnList = "company_id, section")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company"})
public class LandingImage implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 20)
    private Section section;

    @NotNull
    @Min(1)
    @Max(6)
    @Column(name = "position", nullable = false)
    private Integer position;

    @NotBlank
    @Size(max = 500)
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Size(max = 200)
    @Column(name = "alt_text", length = 200)
    private String altText;

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

    public enum Section {
        CAROUSEL("Carrusel", 4),
        HISTORY("Nuestra Historia", 1),
        OFFERS("Lo Que Te Ofrecemos", 6),
        VALUES("Lo Que Nos Define", 3),
        CARD("Tarjeta de Presentación", 2),
        GALLERY("Galería", 6);

        @Getter
        private final String displayName;
        @Getter
        private final int maxPositions;

        Section(String displayName, int maxPositions) {
            this.displayName = displayName;
            this.maxPositions = maxPositions;
        }
    }
}
