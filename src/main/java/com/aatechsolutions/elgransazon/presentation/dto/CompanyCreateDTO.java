package com.aatechsolutions.elgransazon.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new company (multi-tenant)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCreateDTO {

    // ========== COMPANY FIELDS ==========
    @NotBlank(message = "El slug es requerido")
    @Size(min = 3, max = 50, message = "El slug debe tener entre 3 y 50 caracteres")
    @Pattern(regexp = "^[a-z0-9.-]+$", message = "El slug solo puede contener letras minúsculas, números, guiones y puntos")
    private String slug;

    @NotBlank(message = "El nombre es requerido")
    @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
    private String name;

    @Size(max = 200, message = "El dominio no puede exceder 200 caracteres")
    private String customDomain;

    @Email(message = "El email debe ser válido")
    @Size(max = 200, message = "El email no puede exceder 200 caracteres")
    private String senderEmail;

    @Size(max = 100, message = "El nombre del remitente no puede exceder 100 caracteres")
    private String senderName;

    @Email(message = "El email debe ser válido")
    @Size(max = 200, message = "El email no puede exceder 200 caracteres")
    private String contactEmail;

    @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
    private String contactPhone;

    @Size(max = 500, message = "La dirección no puede exceder 500 caracteres")
    private String address;

    @Size(max = 20, message = "El RFC no puede exceder 20 caracteres")
    private String rfc;

    private String timezone = "America/Mexico_City";

    // ========== SYSTEM CONFIGURATION FIELDS ==========
    private java.math.BigDecimal taxRate = java.math.BigDecimal.valueOf(16.00);

    // ========== ADMIN USER FIELDS ==========
    @NotBlank(message = "El nombre del administrador es requerido")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    private String adminFirstName;

    @NotBlank(message = "El apellido del administrador es requerido")
    @Size(min = 2, max = 50, message = "El apellido debe tener entre 2 y 50 caracteres")
    private String adminLastName;

    @NotBlank(message = "El nombre de usuario es requerido")
    @Size(min = 3, max = 50, message = "El nombre de usuario debe tener entre 3 y 50 caracteres")
    private String adminUsername;

    @NotBlank(message = "La contraseña es requerida")
    @Size(min = 6, max = 100, message = "La contraseña debe tener al menos 6 caracteres")
    private String adminPassword;

    // ========== LICENSE FIELDS ==========
    private boolean freeTrial = true;

    @NotBlank(message = "El tipo de paquete es requerido")
    private String packageType = "BASIC";

    @NotBlank(message = "El ciclo de facturación es requerido")
    private String billingCycle = "MONTHLY";

    private int licenseMonths = 1;

    private Double licenseAmount;
}
