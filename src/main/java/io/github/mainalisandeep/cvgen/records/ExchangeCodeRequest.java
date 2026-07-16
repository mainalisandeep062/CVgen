package io.github.mainalisandeep.cvgen.records;

import jakarta.validation.constraints.NotBlank;

public record ExchangeCodeRequest(@NotBlank String code) {
}
