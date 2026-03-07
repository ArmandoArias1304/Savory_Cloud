package com.aatechsolutions.elgransazon.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for changing customer password
 */
@Data
public class ChangePasswordDTO {
    
    @NotBlank(message = "La nueva contraseña es requerida")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String newPassword;
    
    @NotBlank(message = "Debe confirmar la contraseña")
    private String confirmPassword;
    
    /**
     * Validates that both passwords match
     */
    public boolean passwordsMatch() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
