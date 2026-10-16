package com.nova.tech.bank.sphere.dto;

import com.nova.tech.bank.sphere.util.PasswordRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank
    @Size(max = 64)
    private String currentPassword;

    @NotBlank
    @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
    private String newPassword;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "Code must be exactly 6 digits")
    private String otp;
}
