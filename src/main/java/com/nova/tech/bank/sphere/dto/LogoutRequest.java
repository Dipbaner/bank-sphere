package com.nova.tech.bank.sphere.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LogoutRequest {

    @Size(max = 100)
    private String refreshToken;
}
