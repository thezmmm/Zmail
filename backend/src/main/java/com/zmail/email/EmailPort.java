package com.zmail.email;

import com.zmail.model.EmailAccount;

import java.util.List;

public interface EmailPort {
    List<EmailMessage> fetchUnread(EmailAccount account, int maxResults);
    void send(EmailAccount account, EmailDraft draft);
    void archive(EmailAccount account, String messageId);
    void markRead(EmailAccount account, String messageId);
}