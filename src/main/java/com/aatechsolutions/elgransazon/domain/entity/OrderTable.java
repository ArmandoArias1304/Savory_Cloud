package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Mesa ADICIONAL de un pedido con mesas unidas.
 *
 * <p>El modelo es aditivo: {@link Order#getTable()} sigue siendo la mesa principal
 * (así todos los tickets, reportes y vistas existentes siguen funcionando), y esta
 * tabla guarda únicamente las mesas extra que se unieron físicamente para compartir
 * una sola cuenta. Un pedido normal no tiene filas aquí.</p>
 */
@Entity
@Table(name = "order_table", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_table", columnNames = {"id_order", "id_table"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"parentOrder"})
public class OrderTable implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_order_table")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_order", nullable = false)
    private Order parentOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_table", nullable = false)
    private RestaurantTable table;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
