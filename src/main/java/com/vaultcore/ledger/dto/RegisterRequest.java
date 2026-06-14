package com.vaultcore.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String phoneNumber;

    @NotBlank
    private String password;

    private String email;
}
