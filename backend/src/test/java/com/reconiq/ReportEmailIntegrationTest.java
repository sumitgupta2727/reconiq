package com.reconiq;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that the report email really leaves the application as a PDF attachment.
 * A local GreenMail SMTP server replaces Gmail, so the test is hermetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportEmailIntegrationTest {

    private static final String SMTP_USER = "reconiq@test.local";
    private static final String SMTP_PASSWORD = "app-password";

    private static final GreenMail GREEN_MAIL = startGreenMail();

    private static GreenMail startGreenMail() {
        GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP);
        greenMail.setUser(SMTP_USER, SMTP_USER, SMTP_PASSWORD);
        greenMail.start();
        return greenMail;
    }

    @AfterAll
    static void stopGreenMail() {
        GREEN_MAIL.stop();
    }

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> GREEN_MAIL.getSmtp().getPort());
        registry.add("spring.mail.username", () -> SMTP_USER);
        registry.add("spring.mail.password", () -> SMTP_PASSWORD);
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> false);
        registry.add("spring.mail.properties.mail.smtp.starttls.required", () -> false);
        registry.add("reconiq.report.email.enabled", () -> true);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Email endpoint sends the PDF report as an attachment")
    void sendsPdfReportByEmail() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        mockMvc.perform(post("/api/reports/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"finance@test.local\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.to").value("finance@test.local"));

        assertThat(GREEN_MAIL.waitForIncomingEmail(5_000, 1)).isTrue();
        MimeMessage[] messages = GREEN_MAIL.getReceivedMessages();
        assertThat(messages).hasSize(1);

        MimeMessage message = messages[0];
        assertThat(message.getSubject()).contains("ReconIQ reconciliation report");
        assertThat(message.getAllRecipients()[0].toString()).contains("finance@test.local");
        assertThat(message.getFrom()[0].toString()).contains(SMTP_USER);

        Multipart multipart = (Multipart) message.getContent();
        boolean pdfFound = false;
        for (int index = 0; index < multipart.getCount(); index++) {
            BodyPart part = multipart.getBodyPart(index);
            String fileName = part.getFileName();
            if (fileName == null || !fileName.endsWith(".pdf")) {
                continue;
            }
            pdfFound = true;
            assertThat(fileName).startsWith("reconiq-report-");
            assertThat(part.getContentType()).contains("application/pdf");
            byte[] pdf = part.getInputStream().readAllBytes();
            assertThat(pdf.length).isGreaterThan(1_000);
            assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        }
        assertThat(pdfFound).as("PDF attachment present in the message").isTrue();
    }

    @Test
    @DisplayName("Email configuration endpoint reports SMTP as ready")
    void emailConfigShowsConfiguredSender() throws Exception {
        mockMvc.perform(get("/api/reports/email/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.from").value(SMTP_USER));
    }

    @Test
    @DisplayName("An invalid recipient is rejected before any mail is sent")
    void invalidRecipientIsRejected() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        mockMvc.perform(post("/api/reports/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        assertThat(GREEN_MAIL.waitForIncomingEmail(1_000, 1)).isFalse();
    }
}
