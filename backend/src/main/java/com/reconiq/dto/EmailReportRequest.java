package com.reconiq.dto;

import jakarta.validation.constraints.Email;

public record EmailReportRequest(@Email(message = "A valid email address is required") String to) {
}
