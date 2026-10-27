package com.aatechsolutions.elgransazon.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Form backing the reward catalog editor. Keeping it separate from the entity
 * prevents the browser from posting company, item price or stamps the admin
 * did not choose.
 */
@Getter
@Setter
public class LoyaltyRewardForm {

    private Long id;

    @NotNull(message = "Selecciona el producto que se regalará")
    private Long itemMenuId;

    @NotBlank(message = "El nombre de la recompensa es requerido")
    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    private String name;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String description;

    @NotNull(message = "Los sellos requeridos son obligatorios")
    @Min(value = 1, message = "La recompensa debe costar al menos un sello")
    @Max(value = 100, message = "La recompensa no puede costar más de 100 sellos")
    private Integer stampsCost;

    @NotNull(message = "La cantidad de artículos es obligatoria")
    @Min(value = 1, message = "La recompensa debe incluir al menos un artículo")
    @Max(value = 20, message = "La recompensa no puede incluir más de 20 artículos")
    private Integer freeQuantity = 1;

    @Min(value = 0, message = "El orden no puede ser negativo")
    private Integer sortOrder = 0;

    private Boolean active = true;
}
