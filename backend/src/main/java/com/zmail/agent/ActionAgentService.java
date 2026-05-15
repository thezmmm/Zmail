package com.zmail.agent;

import com.zmail.email.EmailMessage;
import com.zmail.service.EmailService;
import com.zmail.service.LlmRetryHelper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionAgentService {

    @Qualifier("summarizeModel")
    private final ChatLanguageModel model;
    private final EmailService emailService;
    private final LlmRetryHelper retry;

    private static final int BODY_LIMIT = 3000;

    /** Draft a reply for a specific email given user instructions. */
    public String draftReply(UUID userId, EmailRef ref, String userInstruction) {
        EmailMessage email = fetchEmail(userId, ref);
        String body = truncate(email != null ? email.body() : "");
        String subject = email != null ? email.subject() : ref.providerId();
        String sender  = email != null ? email.sender()  : "Unknown";

        String prompt = """
                Draft a professional email reply based on the user's instruction.
                Return ONLY the reply body (no subject line, no greeting header).

                Original email
                From: %s
                Subject: %s
                Body:
                %s

                User instruction: %s
                """.formatted(sender, subject, body, userInstruction);

        return retry.call(
                "draftReply:" + ref.providerId(),
                () -> model.chat(prompt).trim(),
                "Thank you for your email. I will get back to you shortly."
        );
    }

    /** Answer a question about a specific email. */
    public String analyzeEmail(UUID userId, EmailRef ref, String question) {
        EmailMessage email = fetchEmail(userId, ref);
        String body = truncate(email != null ? email.body() : "");
        String subject = email != null ? email.subject() : ref.providerId();
        String sender  = email != null ? email.sender()  : "Unknown";

        String prompt = """
                Answer the user's question about the following email. Be concise and helpful.

                Email
                From: %s
                Subject: %s
                Body:
                %s

                Question: %s
                """.formatted(sender, subject, body, question);

        return retry.call(
                "analyzeEmail:" + ref.providerId(),
                () -> model.chat(prompt).trim(),
                "I was unable to analyze this email."
        );
    }

    private EmailMessage fetchEmail(UUID userId, EmailRef ref) {
        try {
            return emailService.fetchById(userId, ref.accountId(), ref.providerId());
        } catch (Exception e) {
            log.warn("Could not fetch email {} for action: {}", ref.providerId(), e.getMessage());
            return null;
        }
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > BODY_LIMIT ? text.substring(0, BODY_LIMIT) + "..." : text;
    }
}