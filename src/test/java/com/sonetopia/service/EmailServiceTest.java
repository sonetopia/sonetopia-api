package com.sonetopia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import com.sonetopia.entity.User;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

class EmailServiceTest {

  @Test
  void sendsHtmlVerificationEmailWithToken() throws Exception {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

    EmailService emailService = new EmailService(
      mailSender,
      true,
      "no-reply@example.com",
      "Sonetopia",
      "",
      "https://api.example.com/api/auth/verify-email"
    );

    User user = new User();
    user.setName("Test User");
    user.setEmail("user@example.com");

    emailService.sendVerificationEmail(user, "abc.def");

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());

    MimeMessage message = captor.getValue();
    message.saveChanges();
    InternetAddress from = (InternetAddress) message.getFrom()[0];
    assertEquals("no-reply@example.com", from.getAddress());
    assertEquals("Sonetopia", from.getPersonal());
    assertEquals("Verify your Sonetopia email", message.getSubject());
    assertEquals("user@example.com", ((InternetAddress) message.getAllRecipients()[0]).getAddress());

    String[] plainText = new String[1];
    String[] html = new String[1];
    collectTextParts(message, plainText, html);

    assertNotNull(plainText[0]);
    assertNotNull(html[0]);
    assertTrue(plainText[0].contains("token=abc.def"));
    assertTrue(html[0].contains("token=abc.def"));
    assertTrue(html[0].contains("Verify your email address"));
    assertTrue(html[0].contains("Sonetopia"));
  }

  private static void collectTextParts(Part part, String[] plainText, String[] html) throws Exception {
    Object content = part.getContent();
    if (content instanceof Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        collectTextParts(multipart.getBodyPart(i), plainText, html);
      }
      return;
    }

    if (content instanceof String text) {
      if (part.isMimeType("text/plain")) {
        plainText[0] = text;
      } else if (part.isMimeType("text/html")) {
        html[0] = text;
      }
    }
  }
}
