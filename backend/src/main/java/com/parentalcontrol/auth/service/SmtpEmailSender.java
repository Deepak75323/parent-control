package com.parentalcontrol.auth.service;

import com.parentalcontrol.common.util.SensitiveDataRedactor;
import com.parentalcontrol.config.SmtpProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSender implements EmailSender {

  private final SmtpProperties smtpProperties;
  private final Environment environment;

  @Override
  public void sendPasswordResetEmail(String toEmail, String resetLink) {
    sendPlainTextEmail(
        toEmail, "Reset your Parent Control password", buildPasswordResetBody(resetLink));
  }

  @Override
  public void sendEmailVerificationEmail(String toEmail, String verifyLink) {
    sendPlainTextEmail(
        toEmail, "Verify your Parent Control email", buildVerificationBody(verifyLink));
  }

  private void sendPlainTextEmail(String toEmail, String subject, String body) {
    if (isSmtpConfigured()) {
      try {
        sendMessage(toEmail, subject, body);
        log.info("Email sent to {} subject={}", SensitiveDataRedactor.email(toEmail), subject);
        return;
      } catch (IOException ex) {
        log.error(
            "Failed to send email to {} subject={}",
            SensitiveDataRedactor.email(toEmail),
            subject,
            ex);
        return;
      }
    }

    if (!isProdProfile()) {
      log.warn(
          "[DEV] Email for {} subject={} — link: {}",
          SensitiveDataRedactor.email(toEmail),
          subject,
          extractLink(body));
      return;
    }
    log.error(
        "SMTP is not configured — email not sent for {}", SensitiveDataRedactor.email(toEmail));
  }

  private static String extractLink(String body) {
    int idx = body.indexOf("http");
    if (idx < 0) {
      idx = body.indexOf("parentalcontrol://");
    }
    if (idx < 0) {
      return "(see body)";
    }
    int end = body.indexOf('\n', idx);
    return end > idx ? body.substring(idx, end).trim() : body.substring(idx).trim();
  }

  private void sendMessage(String toEmail, String subject, String body) throws IOException {
    try (SmtpSession session = SmtpSession.connect(smtpProperties)) {
      session.expect(220);
      session.send("EHLO parental-control-api");
      session.expect(250);
      if (smtpProperties.getPort() == 587) {
        session.send("STARTTLS");
        session.expect(220);
        session.startTls();
        session.send("EHLO parental-control-api");
        session.expect(250);
      }
      if (smtpProperties.getUsername() != null && !smtpProperties.getUsername().isBlank()) {
        String auth =
            Base64.getEncoder()
                .encodeToString(
                    ("\0" + smtpProperties.getUsername() + "\0" + smtpProperties.getPassword())
                        .getBytes(StandardCharsets.UTF_8));
        session.send("AUTH PLAIN " + auth);
        session.expect(235);
      }
      session.send("MAIL FROM:<" + smtpProperties.getFromAddress() + ">");
      session.expect(250);
      session.send("RCPT TO:<" + toEmail + ">");
      session.expect(250);
      session.send("DATA");
      session.expect(354);
      session.sendRaw(
          "From: "
              + smtpProperties.getFromAddress()
              + "\r\n"
              + "To: "
              + toEmail
              + "\r\n"
              + "Subject: "
              + subject
              + "\r\n"
              + "Content-Type: text/plain; charset=UTF-8\r\n"
              + "\r\n"
              + body
              + "\r\n"
              + ".");
      session.expect(250);
      session.send("QUIT");
    }
  }

  private static String buildPasswordResetBody(String resetLink) {
    return """
                We received a request to reset your Parent Control password.

                Open this link to choose a new password (valid for a limited time):
                %s

                If you did not request this, you can ignore this email.
                """
        .formatted(resetLink);
  }

  private static String buildVerificationBody(String verifyLink) {
    return """
                Welcome to Parent Control.

                Verify your email address to start your free trial:
                %s

                If you did not create this account, you can ignore this email.
                """
        .formatted(verifyLink);
  }

  private boolean isSmtpConfigured() {
    return smtpProperties.getHost() != null && !smtpProperties.getHost().isBlank();
  }

  private boolean isProdProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains("prod");
  }

  private static final class SmtpSession implements AutoCloseable {
    private Socket socket;
    private Writer writer;
    private BufferedReader reader;

    private SmtpSession(Socket socket, Writer writer, BufferedReader reader) {
      this.socket = socket;
      this.writer = writer;
      this.reader = reader;
    }

    static SmtpSession connect(SmtpProperties props) throws IOException {
      Socket socket;
      if (props.getPort() == 465) {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        socket = factory.createSocket(props.getHost(), props.getPort());
        ((SSLSocket) socket).startHandshake();
      } else {
        socket = new Socket(props.getHost(), props.getPort());
      }
      Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
      BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      return new SmtpSession(socket, writer, reader);
    }

    void startTls() throws IOException {
      SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
      SSLSocket sslSocket =
          (SSLSocket)
              factory.createSocket(
                  socket, socket.getInetAddress().getHostName(), socket.getPort(), true);
      sslSocket.startHandshake();
      writer.close();
      reader.close();
      socket.close();
      socket = sslSocket;
      writer = new OutputStreamWriter(sslSocket.getOutputStream(), StandardCharsets.UTF_8);
      reader =
          new BufferedReader(
              new InputStreamReader(sslSocket.getInputStream(), StandardCharsets.UTF_8));
    }

    void send(String command) throws IOException {
      writer.write(command + "\r\n");
      writer.flush();
    }

    void sendRaw(String payload) throws IOException {
      writer.write(payload + "\r\n");
      writer.flush();
    }

    void expect(int code) throws IOException {
      String line = reader.readLine();
      if (line == null || !line.startsWith(String.valueOf(code))) {
        throw new IOException("Unexpected SMTP response: " + line);
      }
    }

    @Override
    public void close() throws IOException {
      writer.close();
      reader.close();
      socket.close();
    }
  }
}
