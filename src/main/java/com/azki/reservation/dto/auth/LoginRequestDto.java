package com.azki.reservation.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Data Transfer Object for login requests
 */
@Data
public class LoginRequestDto {

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email format is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
