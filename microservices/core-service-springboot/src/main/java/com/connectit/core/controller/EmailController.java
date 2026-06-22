package com.connectit.core.controller;

import com.connectit.core.model.*;
import com.connectit.core.repository.*;
import com.connectit.core.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    private final EmailService                 emailService;
    private final EmailLogRepository           emailLogRepo;
    private final NotificationQueueRepository  queueRepo;
    private final CompanyEmailConfigRepository configRepo;
    private final org.springframework.mail.javamail.JavaMailSender mailSender;

    @Value("${app.mail.from:support@technosprint.net}")
    private String defaultFrom;

    @Value("${app.mail.from-name:Manage My Desk}")
    private String defaultFromName;

    @GetMapping("/email/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(emailService.getHealth());
    }

    @GetMapping("/email/logs")
    public ResponseEntity<?> logs(@RequestParam(defaultValue="50") int limit) {
        return ResponseEntity.ok(emailLogRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)));
    }

    @GetMapping("/email/queue")
    public ResponseEntity<?> queue() {
        List<Object[]> statsRaw = queueRepo.countByStatus();
        List<Map<String,Object>> stats = statsRaw.stream()
            .map(r -> Map.of("status", r[0], "count", r[1]))
            .toList();
        var items = queueRepo.findAll(PageRequest.of(0, 50)).getContent();
        return ResponseEntity.ok(Map.of("items", items, "stats", stats));
    }

    @PostMapping("/email/queue/process")
    public ResponseEntity<?> processQueue() {
        emailService.processQueue();
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/email/queue/retry-failed")
    public ResponseEntity<?> retryFailed() {
        queueRepo.findByStatus("failed").forEach(q -> {
            q.setStatus("retry");
            q.setRetryCount(0);
            q.setNextRetryAt(null);
            queueRepo.save(q);
        });
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/email/send-test")
    public ResponseEntity<?> sendTest(@RequestBody Map<String,String> body) {
        String to = body.getOrDefault("to", "info@technosprint.net");
        emailService.sendAsync(to,
            "[TEST] Manage My Desk Email Test",
            "<div style='font-family:sans-serif;padding:20px'><h2 style='color:#2563eb'>&#x2705; Email Integration Working</h2>" +
            "<p>This confirms the Manage My Desk email integration is operational.</p>" +
            "<p>Sent from: " + defaultFrom + " (" + defaultFromName + ")</p>" +
            "<p>Sent at: " + java.time.LocalDateTime.now() + "</p></div>"
        );
        return ResponseEntity.ok(Map.of("success", true, "message", "Test email sent to " + to));
    }

    @PostMapping("/email/send-note")
    public ResponseEntity<?> sendNote(@RequestBody Map<String,String> body) {
        emailService.sendAsync(body.get("to"), body.get("subject"), body.get("body"));
        return ResponseEntity.ok(Map.of("message", "Email queued"));
    }

    /**
     * Test SMTP credentials — either the current app config or custom credentials.
     * Body (optional): { "host": "smtp-relay.brevo.com", "port": "587", "username": "...", "password": "..." }
     */
    @PostMapping("/email/smtp-test")
    public ResponseEntity<?> testCurrentSmtp(@RequestBody(required = false) Map<String,String> body) {
        String host, username, password;
        int port;

        if (body != null && body.containsKey("host")) {
            host     = body.getOrDefault("host", "smtp-relay.brevo.com");
            port     = Integer.parseInt(body.getOrDefault("port", "587"));
            username = body.getOrDefault("username", "");
            password = body.getOrDefault("password", "");
        } else {
            JavaMailSenderImpl impl = (JavaMailSenderImpl) mailSender;
            host     = impl.getHost();
            port     = impl.getPort();
            username = impl.getUsername();
            password = impl.getPassword();
        }

        log.info("[SMTP-TEST] Testing SMTP: host={}, port={}, user={}", host, port, username);

        try {
            var props = new java.util.Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.trust", "*");
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "8000");

            final String u = username;
            final String p = password;
            var session = jakarta.mail.Session.getInstance(props, new jakarta.mail.Authenticator() {
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(u, p);
                }
            });
            var transport = session.getTransport("smtp");
            transport.connect(host, port, username, password);
            transport.close();

            log.info("[SMTP-TEST] SUCCESS for {}", host);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "SMTP connection successful! Host: " + host + ":" + port + " | User: " + username
            ));
        } catch (Exception e) {
            log.error("[SMTP-TEST] FAILED: {}", e.getMessage());
            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            String hint = "";
            if (err.contains("Authentication") || err.contains("535") || err.contains("534")) {
                hint = " | HINT: For Brevo: use login email as username and SMTP Key as password. For M365: enable SMTP AUTH in M365 Admin Center per mailbox.";
            } else if (err.toLowerCase().contains("connect")) {
                hint = " | HINT: Cannot connect. Verify host/port or check firewall.";
            }
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "error", err + hint
            ));
        }
    }

    /**
     * Update the live JavaMailSender SMTP credentials at runtime (no server restart needed).
     * Body: { "host": "smtp-relay.brevo.com", "port": "587", "username": "...", "password": "..." }
     */
    @PostMapping("/email/smtp-update")
    public ResponseEntity<?> updateSmtpConfig(@RequestBody Map<String,String> body) {
        try {
            String host     = body.getOrDefault("host", "smtp-relay.brevo.com");
            int    port     = Integer.parseInt(body.getOrDefault("port", "587"));
            String username = body.get("username");
            String password = body.get("password");

            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "username and password are required"));
            }

            // Test before applying
            var props = new java.util.Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", "*");
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "8000");

            final String u = username;
            final String p = password;
            var session = jakarta.mail.Session.getInstance(props, new jakarta.mail.Authenticator() {
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(u, p);
                }
            });
            var transport = session.getTransport("smtp");
            transport.connect(host, port, username, password);
            transport.close();

            // Apply to live mail sender
            JavaMailSenderImpl impl = (JavaMailSenderImpl) mailSender;
            impl.setHost(host);
            impl.setPort(port);
            impl.setUsername(username);
            impl.setPassword(password);

            Properties smtpProps = impl.getJavaMailProperties();
            smtpProps.put("mail.transport.protocol", "smtp");
            smtpProps.put("mail.smtp.auth", "true");
            smtpProps.put("mail.smtp.starttls.enable", "true");
            smtpProps.put("mail.smtp.starttls.required", "true");
            smtpProps.put("mail.smtp.ssl.trust", "*");
            smtpProps.put("mail.smtp.connectiontimeout", "10000");
            smtpProps.put("mail.smtp.timeout", "10000");

            log.info("[SMTP-UPDATE] Live SMTP updated: host={}, port={}, user={}", host, port, username);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "SMTP credentials updated and verified! Emails will now be sent from " + defaultFrom + " (" + defaultFromName + ") via " + host
            ));
        } catch (Exception e) {
            log.error("[SMTP-UPDATE] Failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "error", "SMTP test failed: " + e.getMessage()
            ));
        }
    }

    // ── Email Configs ──────────────────────────────────────────────────────────
    @GetMapping("/email-configs")
    public ResponseEntity<?> listConfigs() {
        return ResponseEntity.ok(configRepo.findByIsActiveTrueOrderByIsDefaultDescCompanyNameAsc());
    }

    @PostMapping("/email-configs")
    public ResponseEntity<?> createConfig(@RequestBody CompanyEmailConfig cfg) {
        if (Boolean.TRUE.equals(cfg.getIsDefault())) {
            configRepo.findAll().forEach(c -> { c.setIsDefault(false); configRepo.save(c); });
        }
        return ResponseEntity.status(201).body(configRepo.save(cfg));
    }

    @PutMapping("/email-configs/{id}")
    public ResponseEntity<?> updateConfig(@PathVariable Long id, @RequestBody CompanyEmailConfig cfg) {
        return configRepo.findById(id).map(existing -> {
            if (Boolean.TRUE.equals(cfg.getIsDefault())) {
                configRepo.findAll().forEach(c -> { if (!c.getId().equals(id)) { c.setIsDefault(false); configRepo.save(c); } });
            }
            cfg.setId(id);
            return ResponseEntity.ok((Object) configRepo.save(cfg));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/email-configs/{id}")
    public ResponseEntity<?> deleteConfig(@PathVariable Long id) {
        configRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/email-configs/test")
    public ResponseEntity<?> testConfig(@RequestBody CompanyEmailConfig cfg) {
        boolean smtpOk = false;
        boolean imapOk = false;
        String smtpErr = null;
        String imapErr = null;

        // 1. SMTP Test
        try {
            var props = new java.util.Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.connectiontimeout", "4000");
            props.put("mail.smtp.timeout", "4000");
            final String smtpUser = cfg.getSmtpUser();
            final String smtpPass = cfg.getSmtpPass();
            var session = jakarta.mail.Session.getInstance(props, new jakarta.mail.Authenticator() {
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(smtpUser, smtpPass);
                }
            });
            var transport = session.getTransport("smtp");
            transport.connect(cfg.getSmtpHost(), cfg.getSmtpPort(), smtpUser, smtpPass);
            transport.close();
            smtpOk = true;
        } catch (Exception e) {
            smtpErr = e.getMessage();
        }

        // 2. IMAP Test
        try {
            var props = new java.util.Properties();
            String protocol = "imaps";
            if ("NONE".equalsIgnoreCase(cfg.getEncryption())) {
                protocol = "imap";
            }
            props.put("mail.store.protocol", protocol);
            props.put("mail.imap.ssl.enable", "imaps".equals(protocol) ? "true" : "false");
            props.put("mail.imap.starttls.enable", "TLS".equalsIgnoreCase(cfg.getEncryption()) ? "true" : "false");
            props.put("mail.imap.ssl.trust", cfg.getImapHost());
            props.put("mail.imaps.ssl.trust", cfg.getImapHost());
            props.put("mail.imap.connectiontimeout", "4000");
            props.put("mail.imap.timeout", "4000");
            props.put("mail.imaps.connectiontimeout", "4000");
            props.put("mail.imaps.timeout", "4000");

            var session = jakarta.mail.Session.getInstance(props, null);
            var store = session.getStore(protocol);
            store.connect(cfg.getImapHost(), cfg.getImapPort(), cfg.getImapUser(), cfg.getImapPass());
            store.close();
            imapOk = true;
        } catch (Exception e) {
            imapErr = e.getMessage();
        }

        if (smtpOk && imapOk) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Both SMTP and IMAP connections successful!"));
        } else {
            List<String> errors = new ArrayList<>();
            if (!smtpOk) errors.add("SMTP: " + smtpErr);
            if (!imapOk) errors.add("IMAP: " + imapErr);
            return ResponseEntity.status(500).body(Map.of("error", String.join(" | ", errors)));
        }
    }
}
