package com.aatechsolutions.elgransazon.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for updating customer profile (without password)
 */
@Data
public class UpdateProfileDTO {
    
    @NotBlank(message = "El nombre completo es requerido")
    private String fullName;
    
    @NotBlank(message = "El nombre de usuario es requerido")
    @Size(min = 3, max = 50, message = "El nombre de usuario debe tener entre 3 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "El nombre de usuario solo puede contener letras, números y guión bajo")
    private String username;
    
    @NotBlank(message = "El teléfono es requerido")
    @Pattern(regexp = "^[+]?[0-9\\-\\s()]{7,20}$", message = "Formato de teléfono inválido")
    private String phone;
}
