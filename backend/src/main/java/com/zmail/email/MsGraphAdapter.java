package com.zmail.email;

import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.FollowupFlag;
import com.microsoft.graph.models.FollowupFlagStatus;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.messages.item.move.MovePostRequestBody;
import com.microsoft.graph.users.item.messages.item.reply.ReplyPostRequestBody;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import com.microsoft.kiota.authentication.AccessTokenProvider;
import com.microsoft.kiota.authentication.AllowedHostsValidator;
import com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import com.zmail.model.EmailAccount;
import com.zmail.service.OAuthTokenService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MsGraphAdapter implements EmailPort {

    private final OAuthTokenService tokenService;

    @Value("${dev.proxy.host:}")
    private String proxyHost;

    @Value("${dev.proxy.port:7890}")
    private int proxyPort;

    public MsGraphAdapter(OAuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public List<EmailMessage> fetchUnread(EmailAccount account, int maxResults) {
        GraphServiceClient client = buildClient(account);
        MessageCollectionResponse response = client.me().messages().get(config -> {
            config.queryParameters.filter = "isRead eq false";
            config.queryParameters.top = maxResults;
            config.queryParameters.select = new String[]{
                    "id", "subject", "sender", "toRecipients", "receivedDateTime", "body"
            };
        });
        if (response == null || response.getValue() == null) return List.of();
        UUID accountId = account.getId();
        return response.getValue().stream()
                .map(msg -> toEmailMessage(msg, accountId))
                .collect(Collectors.toList());
    }

    @Override
    public List<EmailMessage> fetchRecent(EmailAccount account, int maxResults, OffsetDateTime since) {
        GraphServiceClient client = buildClient(account);
        String filter = "receivedDateTime ge " +
                since.toInstant().toString();
        MessageCollectionResponse response = client.me().messages().get(config -> {
            config.queryParameters.filter = filter;
            config.queryParameters.top = maxResults;
            config.queryParameters.select = new String[]{
                    "id", "subject", "sender", "toRecipients", "receivedDateTime", "body"
            };
        });
        if (response == null || response.getValue() == null) return List.of();
        UUID accountId = account.getId();
        return response.getValue().stream()
                .map(msg -> toEmailMessage(msg, accountId))
                .collect(Collectors.toList());
    }

    @Override
    public void send(EmailAccount account, EmailDraft draft) {
        GraphServiceClient client = buildClient(account);

        if (draft.replyToProviderId() != null) {
            // Use the dedicated reply endpoint so Graph API sets threading headers automatically
            ItemBody replyItemBody = new ItemBody();
            replyItemBody.setContentType(BodyType.Text);
            replyItemBody.setContent(draft.body());

            Message replyMessage = new Message();
            replyMessage.setBody(replyItemBody);

            ReplyPostRequestBody replyBody = new ReplyPostRequestBody();
            replyBody.setMessage(replyMessage);
            client.me().messages().byMessageId(draft.replyToProviderId()).reply().post(replyBody);
        } else {
            Message message = new Message();
            message.setSubject(draft.subject());

            ItemBody itemBody = new ItemBody();
            itemBody.setContentType(BodyType.Text);
            itemBody.setContent(draft.body());
            message.setBody(itemBody);

            message.setToRecipients(draft.to().stream().map(addr -> {
                EmailAddress ea = new EmailAddress();
                ea.setAddress(addr);
                Recipient r = new Recipient();
                r.setEmailAddress(ea);
                return r;
            }).collect(Collectors.toList()));

            SendMailPostRequestBody sendBody = new SendMailPostRequestBody();
            sendBody.setMessage(message);
            sendBody.setSaveToSentItems(true);
            client.me().sendMail().post(sendBody);
        }
    }

    @Override
    public void archive(EmailAccount account, String messageId) {
        MovePostRequestBody body = new MovePostRequestBody();
        body.setDestinationId("archive");
        buildClient(account).me().messages().byMessageId(messageId).move().post(body);
    }

    @Override
    public void markRead(EmailAccount account, String messageId) {
        Message patch = new Message();
        patch.setIsRead(true);
        buildClient(account).me().messages().byMessageId(messageId).patch(patch);
    }

    @Override
    public void flag(EmailAccount account, String messageId) {
        Message patch = new Message();
        FollowupFlag followupFlag = new FollowupFlag();
        followupFlag.setFlagStatus(FollowupFlagStatus.Flagged);
        patch.setFlag(followupFlag);
        buildClient(account).me().messages().byMessageId(messageId).patch(patch);
    }

    @Override
    public EmailMessage fetchById(EmailAccount account, String messageId) {
        Message msg = buildClient(account).me().messages().byMessageId(messageId).get(config ->
                config.queryParameters.select = new String[]{
                        "id", "subject", "sender", "toRecipients", "receivedDateTime", "body"
                });
        return toEmailMessage(msg, account.getId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GraphServiceClient buildClient(EmailAccount account) {
        String accessToken = tokenService.getValidAccessToken(account);
        BaseBearerTokenAuthenticationProvider authProvider =
                new BaseBearerTokenAuthenticationProvider(new AccessTokenProvider() {
                    @Override
                    public String getAuthorizationToken(URI targetUrl,
                                                        Map<String, Object> additionalAuthenticationContext) {
                        return accessToken;
                    }

                    @Override
                    public AllowedHostsValidator getAllowedHostsValidator() {
                        return new AllowedHostsValidator("graph.microsoft.com");
                    }
                });
        return new GraphServiceClient(new OkHttpRequestAdapter(authProvider, null, null, buildOkHttpClient()));
    }

    private OkHttpClient buildOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (proxyHost != null && !proxyHost.isBlank()) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }
        return builder.build();
    }

    private EmailMessage toEmailMessage(Message msg, UUID accountId) {
        String sender = Optional.ofNullable(msg.getSender())
                .map(Recipient::getEmailAddress)
                .map(EmailAddress::getAddress)
                .orElse("");
        List<String> recipients = Optional.ofNullable(msg.getToRecipients())
                .orElse(List.of()).stream()
                .map(r -> Optional.ofNullable(r.getEmailAddress())
                        .map(EmailAddress::getAddress).orElse(""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        OffsetDateTime receivedAt = Optional.ofNullable(msg.getReceivedDateTime())
                .orElse(OffsetDateTime.now());
        String bodyContent = Optional.ofNullable(msg.getBody())
                .map(ItemBody::getContent)
                .orElse("");

        return new EmailMessage(
                msg.getId(),
                accountId,
                msg.getSubject(),
                sender,
                recipients,
                receivedAt,
                bodyContent
        );
    }
}