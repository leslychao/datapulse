package io.datapulse.tenancy.domain;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@ConditionalOnBean(JavaMailSender.class)
@RequiredArgsConstructor
public class InvitationMailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${datapulse.mail.from:noreply@datapulse.io}")
    private String fromAddress;

    @Value("${datapulse.mail.invitation-base-url:http://localhost:4200/invitation/accept}")
    private String invitationBaseUrl;

    @Async("mailExecutor")
    public void sendInvitationEmail(String recipientEmail, String inviterName,
                                    String workspaceName, String rawToken, int expirationDays) {
        try {
            Context ctx = new Context();
            ctx.setVariable("inviterName", inviterName);
            ctx.setVariable("workspaceName", workspaceName);
            ctx.setVariable("invitationLink", invitationBaseUrl + "?token=" + rawToken);
            ctx.setVariable("expirationDays", expirationDays);

            String html = templateEngine.process("invitation-email", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipientEmail);
            helper.setSubject("Приглашение в пространство «" + workspaceName + "» — Datapulse");
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Invitation email sent: to={}, workspace={}", recipientEmail, workspaceName);
        } catch (MessagingException e) {
            log.warn("Failed to send invitation email: to={}, workspace={}, error={}",
                    recipientEmail, workspaceName, e.getMessage());
        }
    }
}
