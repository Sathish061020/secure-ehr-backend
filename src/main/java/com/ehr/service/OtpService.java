package com.ehr.service;

import com.ehr.exception.EhrException;
import com.ehr.entity.OtpToken;
import com.ehr.repository.OtpTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpTokenRepository otpTokenRepository;
    private final JavaMailSender mailSender;

    @Value("${app.otp.expiry-minutes}")
    private int otpExpiryMinutes;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Transactional
    public void generateAndSendOtp(String email) {
        // Invalidate any outstanding OTPs for this email
        otpTokenRepository.invalidateAllForEmail(email);

        String otp = generateSecureOtp();

        OtpToken token = OtpToken.builder()
                .email(email)
                .otp(otp)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .used(false)
                .build();

        otpTokenRepository.save(token);
        sendOtpEmail(email, otp);
        log.info("OTP dispatched to: {}", email);
    }

    @Transactional
    public boolean verifyOtp(String email, String submittedOtp) {
        Optional<OtpToken> tokenOpt =
                otpTokenRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email);

        if (tokenOpt.isEmpty()) {
            log.warn("OTP verification failed: no valid token found for {}", email);
            return false;
        }

        OtpToken token = tokenOpt.get();

        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            log.warn("OTP verification failed: expired token for {}", email);
            return false;
        }

        if (!token.getOtp().equals(submittedOtp)) {
            log.warn("OTP verification failed: wrong OTP for {}", email);
            return false;
        }

        token.setUsed(true);
        otpTokenRepository.save(token);
        return true;
    }

    // ── Helpers ──────────────────────────────────────────────

    private String generateSecureOtp() {
        // SecureRandom for cryptographically strong OTP
        int otp = 100000 + new SecureRandom().nextInt(900000);
        return String.valueOf(otp);
    }

    private void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Secure EHR — Your Verification Code");
            message.setText(
                    "Hello,\n\n" +
                    "Your one-time verification code for Secure EHR is:\n\n" +
                    "    " + otp + "\n\n" +
                    "This code expires in " + otpExpiryMinutes + " minutes.\n" +
                    "Do NOT share this code with anyone, including EHR staff.\n\n" +
                    "If you did not attempt to log in, please contact your administrator immediately.\n\n" +
                    "— Secure EHR System"
            );
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new EhrException("Mail server connection failed. Unable to dispatch OTP verification code.", 500);
        }
    }

    public void sendTempPasswordEmail(String toEmail, String tempPassword) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Secure EHR — Welcome! Account Credentials");
            message.setText(
                    "Hello,\n\n" +
                    "An account has been created for you on the Secure EHR portal.\n\n" +
                    "Your login credentials:\n" +
                    "Email: " + toEmail + "\n" +
                    "Temporary Password: " + tempPassword + "\n\n" +
                    "Upon signing in for the first time, you will be prompted to set a new password.\n\n" +
                    "— Secure EHR System"
            );
            mailSender.send(message);
            log.info("Temporary password email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send temporary password email to {}: {}", toEmail, e.getMessage());
        }
    }
}

