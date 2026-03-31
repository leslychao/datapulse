package io.datapulse.tenancy.domain;

import io.datapulse.tenancy.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@ConditionalOnBean(JavaMailSender.class)
@EnableConfigurationProperties(MailProperties.class)
@RequiredArgsConstructor
public class InvitationMailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailProperties mailProperties;

    @Async("mailExecutor")
    public void sendInvitationEmail(String recipientEmail, String inviterName,
                                    String workspaceName, String rawToken, int expirationDays) {
        try {
            Context ctx = new Context();
            ctx.setVariable("inviterName", inviterName);
            ctx.setVariable("workspaceName", workspaceName);
            ctx.setVariable("invitationLink", mailProperties.getInvitationBaseUrl() + "?token=" + rawToken);
            ctx.setVariable("expirationDays", expirationDays);

            String html = templateEngine.process("invitation-email", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailProperties.getFrom());
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
