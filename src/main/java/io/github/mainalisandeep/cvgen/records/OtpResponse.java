package io.github.mainalisandeep.cvgen.records;

public record OtpResponse(String status,
                          String email,
                          String accessToken,
                          String refreshToken) {}