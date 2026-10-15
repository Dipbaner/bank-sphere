package com.nova.tech.bank.sphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefreshRequest {

    @NotBlank
    @Size(max = 100)
    private String refreshToken;
}
