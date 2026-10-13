package com.nova.tech.bank.sphere.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank @Email
    private String email;

    @NotBlank
    @Size(max = 64)
    private String password;
}
