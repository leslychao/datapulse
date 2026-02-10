package io.datapulse.core.service.invite.sender;

import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_COPY_LINK;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_CTA;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_FOOTER;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_LEAD;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_SEND_FAILED;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_SENT;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_SUBJECT;
import static io.datapulse.domain.MessageCodes.INVITE_EMAIL_TITLE;

import io.datapulse.core.config.FreemarkerTemplateRenderer;
import io.datapulse.core.i18n.I18nMessageService;
import io.datapulse.core.properties.InviteProperties;
import io.datapulse.domain.exception.InviteException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
@Slf4j
public class InviteEmailSenderImpl implements InviteEmailSender {

  private static final String HTML_TEMPLATE = "mail/account-invite.ftl";
  private static final Locale INVITE_LOCALE = Locale.forLanguageTag("ru");

  private final JavaMailSender javaMailSender;
  private final MessageSource messageSource;
  private final FreemarkerTemplateRenderer templateRenderer;

  private final InviteProperties inviteProperties;
  private final I18nMessageService i18nMessageService;

  @Override
  public void sendInvite(String email, String rawToken) {
    String inviteUrl = buildInviteUrl(rawToken);

    String subject = msg(INVITE_EMAIL_SUBJECT);

    Map<String, Object> model = Map.of(
        "lang", INVITE_LOCALE.getLanguage(),
        "subject", subject,
        "title", msg(INVITE_EMAIL_TITLE),
        "lead", msg(INVITE_EMAIL_LEAD),
        "cta", msg(INVITE_EMAIL_CTA),
        "copyLink", msg(INVITE_EMAIL_COPY_LINK),
        "footer", msg(INVITE_EMAIL_FOOTER),
        "inviteUrl", inviteUrl
    );

    String html = templateRenderer.render(HTML_TEMPLATE, model);

    sendHtml(email, subject, html);
  }

  private String msg(String code) {
    return messageSource.getMessage(code, null, INVITE_LOCALE);
  }

  private void sendHtml(String email, String subject, String html) {
    MimeMessage mimeMessage = javaMailSender.createMimeMessage();

    try {
      MimeMessageHelper helper =
          new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());

      helper.setFrom(inviteProperties.getEmail().getFrom());
      helper.setTo(email);
      helper.setSubject(subject);
      helper.setText(html, true);

      javaMailSender.send(mimeMessage);

      log.info(
          "{} email={}, template={}, locale={}",
          i18nMessageService.userMessage(INVITE_EMAIL_SENT, EffectiveEmailMasker.mask(email)),
          EffectiveEmailMasker.mask(email),
          HTML_TEMPLATE,
          INVITE_LOCALE.toLanguageTag()
      );
    } catch (MessagingException ex) {
      log.warn(
          "{} email={}, template={}, locale={}, error={}",
          i18nMessageService.userMessage(INVITE_EMAIL_SEND_FAILED,
              EffectiveEmailMasker.mask(email)),
          EffectiveEmailMasker.mask(email),
          HTML_TEMPLATE,
          INVITE_LOCALE.toLanguageTag(),
          i18nMessageService.logMessage(ex)
      );
      throw InviteException.emailDeliveryFailed(email, ex);
    }
  }

  private String buildInviteUrl(String rawToken) {
    String base = normalizeBaseUrl(inviteProperties.getPublicBaseUrl());

    return UriComponentsBuilder
        .fromHttpUrl(base)
        .path("/invites/accept")
        .queryParam("token", rawToken)
        .build()
        .toUriString();
  }

  private String normalizeBaseUrl(String baseUrl) {
    if (baseUrl.endsWith("/")) {
      return baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl;
  }

  static final class EffectiveEmailMasker {

    private EffectiveEmailMasker() {
    }

    static String mask(String email) {
      int atIndex = email.indexOf('@');
      if (atIndex <= 1) {
        return "***";
      }

      String localPart = email.substring(0, atIndex);
      String domainPart = email.substring(atIndex);

      return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + domainPart;
    }
  }
}
