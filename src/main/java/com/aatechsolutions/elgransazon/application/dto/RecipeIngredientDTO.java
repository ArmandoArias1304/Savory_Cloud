package com.aatechsolutions.elgransazon.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Una fila de la receta que llega en el campo {@code recipeJson} del formulario de platillos.
 *
 * <p>El formulario manda la receta completa en un solo campo (una sola parte del multipart)
 * porque Tomcat limita el número de partes por petición ({@code Connector.maxPartCount}) y antes
 * cada ingrediente añadía tres partes del formulario ({@code ingredientIds}, {@code quantities} y
 * {@code units}). La unidad no viaja en el JSON: se toma del propio ingrediente al guardar, así no
 * puede quedar desincronizada con el inventario.
 *
 * @param ingredientId id del ingrediente del inventario
 * @param quantity     cantidad que consume una porción del platillo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecipeIngredientDTO(Long ingredientId, BigDecimal quantity) {
}
