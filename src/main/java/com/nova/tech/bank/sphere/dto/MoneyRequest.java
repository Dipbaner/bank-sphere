package com.nova.tech.bank.sphere.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MoneyRequest {

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    @Size(max = 140, message = "Description can be at most 140 characters")
    private String description;
}