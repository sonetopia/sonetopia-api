package com.sonetopia.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Year;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import com.sonetopia.entity.User;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

  private static final String TEMPLATE_PATH = "templates/email/verification.html";

  private final JavaMailSender mailSender;
  private final boolean enabled;
  private final String from;
  private final String fromName;
  private final String logoUrl;
  private final String verificationUrl;
  private final String template;

  public EmailService(
      JavaMailSender mailSender,
      @Value("${app.email.enabled:true}") boolean enabled,
      @Value("${app.email.from}") String from,
      @Value("${app.email.from-name:Sonetopia}") String fromName,
      @Value("${app.email.logo-url:}") String logoUrl,
      @Value("${app.email.verification-url}") String verificationUrl
  ) {
    this.mailSender = mailSender;
    this.enabled = enabled;
    this.from = from;
    this.fromName = fromName;
    this.logoUrl = logoUrl;
    this.verificationUrl = verificationUrl;
    this.template = loadTemplate();
  }

  public void sendVerificationEmail(User user, String token) {
    if (!enabled) {
      return;
    }

    String url = UriComponentsBuilder.fromUriString(verificationUrl)
        .queryParam("token", token)
        .build()
        .toUriString();

    String displayName = user.getName() == null ? user.getUsername() : user.getName();

    MimeMessage message = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(from, fromName);
      helper.setTo(user.getEmail());
      helper.setSubject("Verify your " + fromName + " email");
      helper.setText(buildPlainText(displayName, url), buildHtml(displayName, url));
    } catch (MessagingException | UnsupportedEncodingException exception) {
      throw new MailPreparationException("Failed to build verification email", exception);
    }

    mailSender.send(message);
  }

  private String buildPlainText(String displayName, String url) {
    return """
        Hi %s,

        Verify your %s account by opening this link:
        %s

        If you did not create this account, you can ignore this email.

        (c) %d %s
        """.formatted(displayName, fromName, url, Year.now().getValue(), fromName);
  }

  private String buildHtml(String displayName, String url) {
    String logo = logoUrl.isBlank()
        ? "<div style=\"width:48px;height:48px;background-color:#d46420;border-radius:14px;color:#fffbf4;font-size:26px;line-height:48px;text-align:center;font-weight:700;\">&#9835;</div>"
        : "<img src=\"" + HtmlUtils.htmlEscape(logoUrl) + "\" width=\"48\" height=\"48\" alt=\""
            + HtmlUtils.htmlEscape(fromName) + "\" style=\"display:block;border-radius:14px;\">";

    return template
        .replace("{{logo}}", logo)
        .replace("{{appName}}", HtmlUtils.htmlEscape(fromName))
        .replace("{{name}}", HtmlUtils.htmlEscape(displayName))
        .replace("{{verificationUrl}}", HtmlUtils.htmlEscape(url))
        .replace("{{year}}", String.valueOf(Year.now().getValue()));
  }

  private static String loadTemplate() {
    try (InputStream inputStream = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
      return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to load email template: " + TEMPLATE_PATH, exception);
    }
  }
}
