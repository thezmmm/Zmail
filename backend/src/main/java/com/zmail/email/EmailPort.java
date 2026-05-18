package com.zmail.email;

import com.zmail.model.EmailAccount;

import java.time.OffsetDateTime;
import java.util.List;

public interface EmailPort {
    List<EmailMessage> fetchUnread(EmailAccount account, int maxResults);
    List<EmailMessage> fetchRecent(EmailAccount account, int maxResults, OffsetDateTime since);
    void send(EmailAccount account, EmailDraft draft);
    void archive(EmailAccount account, String messageId);
    void markRead(EmailAccount account, String messageId);
    void flag(EmailAccount account, String messageId);
    EmailMessage fetchById(EmailAccount account, String messageId);
}