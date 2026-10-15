package com.nova.tech.bank.sphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BackupCodeRequest {

    @NotBlank
    @Size(max = 20)
    private String code;
}
