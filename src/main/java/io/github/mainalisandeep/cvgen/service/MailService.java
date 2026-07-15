package io.github.mainalisandeep.cvgen.service;

public interface MailService {

    void sendOtpEmail(String to, String otp, int expiryMinutes, String purpose);


}
