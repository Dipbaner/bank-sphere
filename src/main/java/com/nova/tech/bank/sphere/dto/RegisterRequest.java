package com.nova.tech.bank.sphere.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z ]{2,60}$",
            message = "Name must be 2-60 letters")
    private String name;

    @NotBlank @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "^[6-9]\\d{9}$",
            message = "Enter a valid 10-digit mobile number")
    private String phone;

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$",
            message = "Password needs 8+ chars with upper, lower, digit and special character")
    private String password;
}
