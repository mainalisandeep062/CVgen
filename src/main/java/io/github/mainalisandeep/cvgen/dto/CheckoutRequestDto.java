package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.PaymentGateway;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Buy one pack through one gateway. Price and credits come from the pack, never from the client. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CheckoutRequestDto {

    @NotNull(message = "{validation.checkout.pack.required}")
    private UUID packId;

    @NotNull(message = "{validation.checkout.gateway.required}")
    private PaymentGateway gateway;
}
