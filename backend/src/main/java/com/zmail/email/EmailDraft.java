package com.zmail.email;

import java.util.List;

/**
 * @param replyToProviderId null for new emails; set to the original message's provider ID for replies.
 *                          GmailAdapter will fetch thread/Message-ID at send time;
 *                          MsGraphAdapter will use the /reply endpoint.
 */
public record EmailDraft(
        List<String> to,
        String subject,
        String body,
        String replyToProviderId
) {
    /** Convenience factory for new emails (no threading). */
    public static EmailDraft compose(List<String> to, String subject, String body) {
        return new EmailDraft(to, subject, body, null);
    }

    /** Convenience factory for replies. */
    public static EmailDraft reply(List<String> to, String subject, String body, String replyToProviderId) {
        return new EmailDraft(to, subject, body, replyToProviderId);
    }
}
