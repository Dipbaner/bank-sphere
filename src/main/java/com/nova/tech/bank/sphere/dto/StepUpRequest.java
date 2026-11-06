package com.nova.tech.bank.sphere.dto;

import com.nova.tech.bank.sphere.entity.StepUpAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StepUpRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "Code must be exactly 6 digits")
    private String otp;

    @NotNull
    private StepUpAction action;

    @Size(max = 200)
    private String context;
}