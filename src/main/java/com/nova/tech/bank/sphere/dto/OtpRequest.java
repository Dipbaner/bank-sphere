package com.nova.tech.bank.sphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OtpRequest {
    @NotBlank
    @Pattern(regexp = "^\\d{6}$",
            message = "Code must be exactly 6 digits")
    private String otp;
}
