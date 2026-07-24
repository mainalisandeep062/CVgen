package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.message.CustomMessageSource;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.enums.OtpPurpose;
import io.github.mainalisandeep.cvgen.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);
    private static final String OTP_TEMPLATE = "email/otp-mail";
    private static final String OTP_SUBJECT_KEY = "mail.otp.subject";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final CustomMessageSource customMessageSource;

    @Value("${spring.mail.from:no-reply@cvgen.io}")
    private String fromAddress;

    @Override
    @Async("applicationTaskExecutor")
    public void sendOtpEmail(String to, String otp, int expiryMinutes, OtpPurpose purpose) {
        try {
            Context context = new Context();
            context.setVariable("otp", otp);
            context.setVariable("expiryMinutes", expiryMinutes);
            context.setVariable("purpose", purpose.name());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(customMessageSource.get(OTP_SUBJECT_KEY));
            helper.setText(templateEngine.process(OTP_TEMPLATE, context), true);

            mailSender.send(message);
        } catch (MessagingException | RuntimeException exception) {
            // Runs on the async executor: nothing can catch a rethrow, so log and let the
            // caller resend. The OTP itself is already persisted and still valid.
            log.error(customMessageSource.get(ErrorConstantValue.MAIL_SEND_FAILED, to), exception);
        }
    }
}
