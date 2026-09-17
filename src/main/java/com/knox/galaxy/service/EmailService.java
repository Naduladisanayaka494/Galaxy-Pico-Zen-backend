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
        this.fromAddress = (fromAddress == null || fromAddress.isBlank()) ? "noreply@example.com" : fromAddress;
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
        } catch (org.springframework.mail.MailException e) {
            System.err.println("WARNING: Could not send welcome email to " + toEmail + ". " + e.getMessage());
            e.printStackTrace();
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

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Reset your Galaxy password");
            
            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
            String text = "We received a request to reset your Galaxy password.\n\n"
                    + "Click the link below to choose a new password:\n"
                    + resetLink + "\n\n"
                    + "This link will expire in 1 hour. If you didn't request a password reset, you can safely ignore this email.\n\n"
                    + "— Galaxy, by KNOX";
            
            helper.setText(text, false);
            mailSender.send(message);
        } catch (org.springframework.mail.MailException e) {
            System.err.println("WARNING: Could not send password reset email to " + toEmail + ". " + e.getMessage());
            System.err.println("Reset Link that would have been sent: " + frontendUrl + "/reset-password?token=" + resetToken);
            e.printStackTrace();
        } catch (Exception e) {
            throw new MailSendException("Failed to send password reset email to " + toEmail, e);
        }
    }
}
