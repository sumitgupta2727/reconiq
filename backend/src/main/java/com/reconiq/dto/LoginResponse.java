package com.reconiq.dto;

public record LoginResponse(
        Long id,
        String employeeId,
        String name,
        String email,
        String role) {
}
