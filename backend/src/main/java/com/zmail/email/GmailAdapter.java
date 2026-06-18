package com.zmail.email;

import com.google.api.client.googleapis.GoogleUtils;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import jakarta.annotation.PostConstruct;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.zmail.model.EmailAccount;
import com.zmail.service.OAuthTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GmailAdapter implements EmailPort {

    private final OAuthTokenService tokenService;

    @Value("${dev.proxy.host:}")
    private String proxyHost;

    @Value("${dev.proxy.port:7890}")
    private int proxyPort;

    private HttpTransport transport;

    public GmailAdapter(OAuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostConstruct
    void init() {
        try {
            this.transport = (proxyHost == null || proxyHost.isBlank())
                    ? GoogleNetHttpTransport.newTrustedTransport()
                    : new NetHttpTransport.Builder()
                            .trustCertificates(GoogleUtils.getCertificateTrustStore())
                            .setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)))
                            .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize Gmail HTTP transport", e);
        }
    }

    @Override
    public List<EmailMessage> fetchUnread(EmailAccount account, int maxResults) {
        try {
            Gmail service = buildService(account);
            ListMessagesResponse response = service.users().messages()
                    .list("me")
                    .setQ("is:unread in:inbox")
                    .setMaxResults((long) maxResults)
                    .execute();

            if (response.getMessages() == null) return List.of();

            List<EmailMessage> result = new ArrayList<>();
            for (Message ref : response.getMessages()) {
                Message full = service.users().messages()
                        .get("me", ref.getId())
                        .setFormat("full")
                        .execute();
                result.add(toEmailMessage(full, account.getId()));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Gmail messages", e);
        }
    }

    @Override
    public List<EmailMessage> fetchRecent(EmailAccount account, int maxResults, OffsetDateTime since) {
        try {
            Gmail service = buildService(account);
            ListMessagesResponse response = service.users().messages()
                    .list("me")
                    .setQ("after:" + since.toEpochSecond())
                    .setMaxResults((long) maxResults)
                    .execute();

            if (response.getMessages() == null) return List.of();

            List<EmailMessage> result = new ArrayList<>();
            for (Message ref : response.getMessages()) {
                Message full = service.users().messages()
                        .get("me", ref.getId())
                        .setFormat("full")
                        .execute();
                result.add(toEmailMessage(full, account.getId()));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch recent Gmail messages", e);
        }
    }

    @Override
    public List<EmailMessage> fetchBefore(EmailAccount account, int maxResults, OffsetDateTime before) {
        try {
            Gmail service = buildService(account);
            ListMessagesResponse response = service.users().messages()
                    .list("me")
                    .setQ("before:" + before.toEpochSecond())
                    .setMaxResults((long) maxResults)
                    .execute();

            if (response.getMessages() == null) return List.of();

            List<EmailMessage> result = new ArrayList<>();
            for (Message ref : response.getMessages()) {
                Message full = service.users().messages()
                        .get("me", ref.getId())
                        .setFormat("full")
                        .execute();
                result.add(toEmailMessage(full, account.getId()));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch older Gmail messages", e);
        }
    }

    @Override
    public void send(EmailAccount account, EmailDraft draft) {
        try {
            Gmail service = buildService(account);
            Message message = new Message();

            if (draft.replyToProviderId() != null) {
                // Fetch original message metadata for threading headers
                Message original = service.users().messages()
                        .get("me", draft.replyToProviderId())
                        .setFormat("metadata")
                        .setMetadataHeaders(List.of("Message-ID", "References"))
                        .execute();
                List<MessagePartHeader> origHeaders = original.getPayload() != null
                        ? original.getPayload().getHeaders() : List.of();
                String inReplyTo = getHeader(origHeaders, "Message-ID");
                String existingRefs = getHeader(origHeaders, "References");
                String references = existingRefs.isBlank() ? inReplyTo : existingRefs + " " + inReplyTo;
                message.setThreadId(original.getThreadId());
                message.setRaw(buildRawEmail(account.getAccountEmail(), draft.to(),
                        draft.subject(), draft.body(), inReplyTo, references));
            } else {
                message.setRaw(buildRawEmail(account.getAccountEmail(), draft.to(),
                        draft.subject(), draft.body(), null, null));
            }

            service.users().messages().send("me", message).execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to send Gmail message", e);
        }
    }

    @Override
    public void archive(EmailAccount account, String messageId) {
        modifyLabels(account, messageId, List.of(), List.of("INBOX"));
    }

    @Override
    public void markRead(EmailAccount account, String messageId) {
        modifyLabels(account, messageId, List.of(), List.of("UNREAD"));
    }

    @Override
    public void flag(EmailAccount account, String messageId) {
        modifyLabels(account, messageId, List.of("STARRED"), List.of());
    }

    @Override
    public EmailMessage fetchById(EmailAccount account, String messageId) {
        try {
            Gmail service = buildService(account);
            Message full = service.users().messages()
                    .get("me", messageId)
                    .setFormat("full")
                    .execute();
            return toEmailMessage(full, account.getId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Gmail message " + messageId, e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Gmail buildService(EmailAccount account) {
        String accessToken = tokenService.getValidAccessToken(account);
        return new Gmail.Builder(
                buildTransport(),
                GsonFactory.getDefaultInstance(),
                req -> req.getHeaders().setAuthorization("Bearer " + accessToken)
        ).setApplicationName("Zmail").build();
    }

    private HttpTransport buildTransport() {
        return transport;
    }

    private void modifyLabels(EmailAccount account, String messageId,
                               List<String> addLabels, List<String> removeLabels) {
        try {
            Gmail service = buildService(account);
            ModifyMessageRequest req = new ModifyMessageRequest()
                    .setAddLabelIds(addLabels)
                    .setRemoveLabelIds(removeLabels);
            service.users().messages().modify("me", messageId, req).execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to modify Gmail message labels", e);
        }
    }

    private EmailMessage toEmailMessage(Message message, UUID accountId) {
        MessagePart payload = message.getPayload();
        if (payload == null) {
            return new EmailMessage(message.getId(), accountId, "", "", List.of(),
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(
                            message.getInternalDate() != null ? message.getInternalDate() : 0L),
                            ZoneId.systemDefault()), "");
        }
        List<MessagePartHeader> headers = payload.getHeaders() != null ? payload.getHeaders() : List.of();
        return new EmailMessage(
                message.getId(),
                accountId,
                getHeader(headers, "Subject"),
                getHeader(headers, "From"),
                parseAddressList(getHeader(headers, "To")),
                OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(message.getInternalDate() != null ? message.getInternalDate() : 0L),
                        ZoneId.systemDefault()),
                extractBody(payload)
        );
    }

    private String getHeader(List<MessagePartHeader> headers, String name) {
        return headers.stream()
                .filter(h -> name.equalsIgnoreCase(h.getName()))
                .map(MessagePartHeader::getValue)
                .findFirst()
                .orElse("");
    }

    private List<String> parseAddressList(String header) {
        if (header == null || header.isBlank()) return List.of();
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String extractBody(MessagePart payload) {
        if (payload.getBody() != null && payload.getBody().getData() != null) {
            return decode(payload.getBody().getData());
        }
        if (payload.getParts() == null) return "";

        // First pass: prefer text/plain — no HTML tags, better for LLM token efficiency
        for (MessagePart part : payload.getParts()) {
            if ("text/plain".equals(part.getMimeType())
                    && part.getBody() != null && part.getBody().getData() != null) {
                return decode(part.getBody().getData());
            }
        }
        // Second pass: accept text/html, or recurse into nested multipart/*
        // (many real emails are multipart/mixed → multipart/alternative → text/plain)
        for (MessagePart part : payload.getParts()) {
            String mime = part.getMimeType();
            if ("text/html".equals(mime)
                    && part.getBody() != null && part.getBody().getData() != null) {
                return decode(part.getBody().getData());
            }
            if (mime != null && mime.startsWith("multipart/")) {
                String nested = extractBody(part);
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    private String decode(String base64url) {
        return new String(Base64.getUrlDecoder().decode(base64url), StandardCharsets.UTF_8);
    }

    private String buildRawEmail(String from, List<String> to, String subject, String body,
                                  String inReplyTo, String references) {
        String encodedSubject = "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8))
                + "?=";
        StringBuilder raw = new StringBuilder();
        raw.append("From: ").append(from).append("\r\n");
        raw.append("To: ").append(String.join(", ", to)).append("\r\n");
        raw.append("Subject: ").append(encodedSubject).append("\r\n");
        if (inReplyTo != null && !inReplyTo.isBlank()) {
            raw.append("In-Reply-To: ").append(inReplyTo).append("\r\n");
        }
        if (references != null && !references.isBlank()) {
            raw.append("References: ").append(references).append("\r\n");
        }
        raw.append("MIME-Version: 1.0\r\n");
        raw.append("Content-Type: text/plain; charset=UTF-8\r\n");
        raw.append("Content-Transfer-Encoding: base64\r\n");
        raw.append("\r\n");
        raw.append(Base64.getMimeEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.toString().getBytes(StandardCharsets.UTF_8));
    }
}