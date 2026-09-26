package io.github.mainalisandeep.cvgen.records;

/**
 * What a gateway said about a payment when asked server to server.
 *
 * @param outcome     how the order should move
 * @param reference   the gateway's transaction reference, stored on the ledger row
 * @param amountMinor amount the gateway reports as paid, compared with the order before crediting
 * @param detail      the gateway's own status text, kept as the failure reason
 */
public record GatewayVerdict(Outcome outcome, String reference, long amountMinor, String detail) {

    public enum Outcome {
        PAID,
        /** Still in flight on the gateway's side; ask again later. */
        PENDING,
        FAILED,
        CANCELED
    }
}
