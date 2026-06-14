package com.vaultcore.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank
    private String phoneNumber;

    @NotBlank
    private String password;
}
