package com.aayusheklavya.payments.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotBlank @Size(max = 64) String merchantId,
        @NotBlank @Size(max = 64) String customerId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be an ISO 4217 code") String currency) {
}
