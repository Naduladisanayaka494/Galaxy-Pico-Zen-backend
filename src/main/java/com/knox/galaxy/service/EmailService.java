package com.knox.galaxy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${galaxy.mail.from}") String fromAddress,
                        @Value("${galaxy.frontend.url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
    }

    /**
     * @throws MailException if sending fails — the caller decides what a
     * failed send means for the surrounding operation (the tenant is already
     * provisioned either way; this is just notification).
     */
    public void sendTenantWelcomeEmail(String toEmail, String businessName, String loginEmail, String password) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Your Galaxy account is ready");
            helper.setText(body(businessName, loginEmail, password), false);
            mailSender.send(message);
        } catch (Exception e) {
            throw new MailSendException("Failed to send welcome email to " + toEmail, e);
        }
    }

    private String body(String businessName, String loginEmail, String password) {
        return "Hi " + businessName + ",\n\n"
                + "Your Galaxy account has been created. Here are your login details:\n\n"
                + "Login page: " + frontendUrl + "\n"
                + "Email: " + loginEmail + "\n"
                + "Temporary password: " + password + "\n\n"
                + "Please log in and change your password as soon as possible.\n\n"
                + "— Galaxy, by KNOX";
    }

    /**
     * The "forgot password" link. The raw token appears only here and in the
     * recipient's inbox — the server keeps a hash (see PasswordResetService).
     *
     * @throws MailException if sending fails — the caller swallows it so the
     * HTTP response can't be used to tell a real address from an unknown one.
     */
    public void sendPasswordResetEmail(String toEmail, String rawToken, long expiryMinutes) {
        String link = frontendUrl + "/reset-password?token=" + rawToken;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Reset your Galaxy password");
            helper.setText(resetBody(link, expiryMinutes), false);
            mailSender.send(message);
        } catch (Exception e) {
            throw new MailSendException("Failed to send password reset email to " + toEmail, e);
        }
    }

    private String resetBody(String link, long expiryMinutes) {
        return "Hi,\n\n"
                + "We received a request to reset your Galaxy password. Open the link "
                + "below to choose a new one:\n\n"
                + link + "\n\n"
                + "This link expires in " + expiryMinutes + " minutes and can be used once.\n\n"
                + "If you didn't ask for this, you can ignore this email — your "
                + "password stays unchanged.\n\n"
                + "— Galaxy, by KNOX";
    }
}
