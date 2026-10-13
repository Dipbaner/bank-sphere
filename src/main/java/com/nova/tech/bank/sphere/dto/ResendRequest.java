package com.nova.tech.bank.sphere.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendRequest {

    @NotBlank @Email
    private String email;
}
