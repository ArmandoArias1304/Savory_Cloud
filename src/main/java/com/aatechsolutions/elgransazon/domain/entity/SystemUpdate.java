package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A platform update announcement published by the programmer.
 *
 * <p>Updates are GLOBAL: the same version, changelog and audience apply to
 * every company. Only the latest published update drives the version buttons
 * and the automatic modal.</p>
 */
@Entity
@Table(name = "system_updates", uniqueConstraints = {
    @UniqueConstraint(name = "uk_system_update_version", columnNames = "version")
}, indexes = {
    @Index(name = "idx_system_update_published", columnList = "published"),
    @Index(name = "idx_system_update_published_at", columnList = "published_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"targetRoles"})
public class SystemUpdate implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotBlank(message = "La versión es requerida")
    @Size(max = 20, message = "La versión no puede exceder 20 caracteres")
    @Pattern(regexp = "^\\d+(\\.\\d+){1,3}$",
            message = "Use un formato como 1.1.1 (solo números y puntos)")
    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @NotBlank(message = "El título es requerido")
    @Size(max = 150, message = "El título no puede exceder 150 caracteres")
    @Column(name = "title", nullable = false, length = 150)
    private String title;

    /**
     * Changelog. One change per line is rendered as a bullet list.
     */
    @NotBlank(message = "Describe lo que se actualizó")
    @Size(max = 4000, message = "La descripción no puede exceder 4000 caracteres")
    @Column(name = "changes", columnDefinition = "TEXT", nullable = false)
    private String changes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "system_update_roles",
            joinColumns = @JoinColumn(name = "update_id"))
    @Column(name = "role", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<SystemUpdateRole> targetRoles = new HashSet<>();

    @Column(name = "published", nullable = false)
    @Builder.Default
    private Boolean published = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Size(max = 100, message = "El autor no puede exceder 100 caracteres")
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.published == null) {
            this.published = false;
        }
        if (this.targetRoles == null) {
            this.targetRoles = new HashSet<>();
        }
        if (Boolean.TRUE.equals(this.published) && this.publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Changelog split into non-empty lines for the modal/list.
     */
    public List<String> getChangeLines() {
        if (changes == null || changes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(changes.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    /**
     * Changelog split into label/detail pairs so templates can render the
     * short label (text before the first colon) in bold.
     */
    public List<ChangeEntry> getChangeEntries() {
        return getChangeLines().stream()
                .map(ChangeEntry::parse)
                .toList();
    }

    /**
     * One changelog line: {@code label} is the bold headline (with its colon)
     * when the programmer wrote "Label: detail"; otherwise it is null and the
     * whole line is plain {@code detail}.
     */
    @Getter
    @AllArgsConstructor
    public static class ChangeEntry implements Serializable {

        /** Longest label treated as a headline; longer ones stay plain text. */
        private static final int MAX_LABEL_LENGTH = 80;

        private final String label;
        private final String detail;

        static ChangeEntry parse(String line) {
            int separator = line.indexOf(':');
            if (separator > 0 && separator <= MAX_LABEL_LENGTH) {
                String label = line.substring(0, separator).trim();
                if (!label.isEmpty()) {
                    return new ChangeEntry(label + ":", line.substring(separator + 1).trim());
                }
            }
            return new ChangeEntry(null, line.trim());
        }
    }
}
