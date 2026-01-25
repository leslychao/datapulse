package io.datapulse.core.service.account.invite.sender;

import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_COPY_LINK;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_CTA;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_FOOTER;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_LEAD;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_SUBJECT;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_TITLE;

import io.datapulse.core.config.FreemarkerTemplateRenderer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InviteEmailSenderImpl implements InviteEmailSender {

  private static final String HTML_TEMPLATE = "mail/account-invite.ftl";

  private final JavaMailSender javaMailSender;
  private final MessageSource messageSource;
  private final FreemarkerTemplateRenderer templateRenderer;

  @Value("${datapulse.invite.public-base-url}")
  private String publicBaseUrl;

  @Value("${datapulse.invite.email.from}")
  private String fromEmail;

  @Override
  public void sendInvite(String email, String rawToken) {
    Locale locale = requireLocale();
    String inviteUrl = buildInviteUrl(rawToken);

    String subject = msg(INVITE_EMAIL_SUBJECT, locale);

    Map<String, String> model = Map.of(
        "lang", locale.getLanguage(),
        "subject", subject,
        "title", msg(INVITE_EMAIL_TITLE, locale),
        "lead", msg(INVITE_EMAIL_LEAD, locale),
        "cta", msg(INVITE_EMAIL_CTA, locale),
        "copyLink", msg(INVITE_EMAIL_COPY_LINK, locale),
        "footer", msg(INVITE_EMAIL_FOOTER, locale),
        "inviteUrl", inviteUrl
    );

    String html = templateRenderer.render(HTML_TEMPLATE, model);

    sendHtml(email, subject, html);
  }

  private Locale requireLocale() {
    return LocaleContextHolder.getLocale();
  }

  private String msg(String code, Locale locale) {
    return messageSource.getMessage(code, null, locale);
  }

  private void sendHtml(String email, String subject, String html) {
    MimeMessage mimeMessage = javaMailSender.createMimeMessage();
    try {
      MimeMessageHelper helper =
          new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());

      helper.setFrom(fromEmail);
      helper.setTo(email);
      helper.setSubject(subject);
      helper.setText(html, true); // HTML ONLY

      javaMailSender.send(mimeMessage);
    } catch (MessagingException ex) {
      throw new IllegalStateException(
          "Ошибка отправки приглашения на e-mail: " + email, ex
      );
    }
  }

  private String buildInviteUrl(String rawToken) {
    String base = publicBaseUrl.endsWith("/")
        ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
        : publicBaseUrl;

    return base + "/invites/accept?token=" + rawToken;
  }
}
