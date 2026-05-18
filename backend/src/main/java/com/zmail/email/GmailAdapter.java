package com.zmail.email;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.zmail.model.EmailAccount;
import com.zmail.service.OAuthTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class GmailAdapter implements EmailPort {

    private final OAuthTokenService tokenService;

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
    public void send(EmailAccount account, EmailDraft draft) {
        try {
            Gmail service = buildService(account);
            String raw = buildRawEmail(account.getAccountEmail(), draft.to(), draft.subject(), draft.body());
            Message message = new Message();
            message.setRaw(raw);
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

    private Gmail buildService(EmailAccount account) throws GeneralSecurityException, IOException {
        String accessToken = tokenService.getValidAccessToken(account);
        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                req -> req.getHeaders().setAuthorization("Bearer " + accessToken)
        ).setApplicationName("Zmail").build();
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
        List<MessagePartHeader> headers = message.getPayload().getHeaders();
        return new EmailMessage(
                message.getId(),
                accountId,
                getHeader(headers, "Subject"),
                getHeader(headers, "From"),
                parseAddressList(getHeader(headers, "To")),
                OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(message.getInternalDate()),
                        ZoneId.systemDefault()),
                extractBody(message.getPayload())
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
        if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                String mime = part.getMimeType();
                if (("text/plain".equals(mime) || "text/html".equals(mime))
                        && part.getBody() != null && part.getBody().getData() != null) {
                    return decode(part.getBody().getData());
                }
            }
        }
        return "";
    }

    private String decode(String base64url) {
        return new String(Base64.getUrlDecoder().decode(base64url), StandardCharsets.UTF_8);
    }

    private String buildRawEmail(String from, List<String> to, String subject, String body) {
        String encodedSubject = "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8))
                + "?=";
        String raw = "From: " + from + "\r\n"
                + "To: " + String.join(", ", to) + "\r\n"
                + "Subject: " + encodedSubject + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + Base64.getMimeEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}