package com.reconiq.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Employee ID is required") String employeeId,
        @NotBlank(message = "Password is required") String password) {
}
