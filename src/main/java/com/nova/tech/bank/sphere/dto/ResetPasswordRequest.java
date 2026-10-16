package com.nova.tech.bank.sphere.dto;

import com.nova.tech.bank.sphere.util.PasswordRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank
    @Size(max = 100)
    private String token;

    @NotBlank
    @Pattern(regexp = PasswordRules.REGEX,
            message = PasswordRules.MESSAGE)
    private String newPassword;
}
