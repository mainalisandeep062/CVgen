package io.github.mainalisandeep.cvgen.service.impl.payment;

/** Lets tests outside this package compute the signature eSewa expects, without widening production visibility. */
public final class EsewaSignatureProbe {

    private EsewaSignatureProbe() {
    }

    public static String sign(String message, String secret) {
        return EsewaGatewayClient.sign(message, secret);
    }
}
