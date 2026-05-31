package com.zmail.agent.action;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface EmailProcessingAgent {

    @SystemMessage("""
            You are an email classifier. Analyze the provided email and respond with \
            ONLY a valid JSON object (no markdown, no explanation):
            {
              "category": "<WORK|PERSONAL|FINANCE|PROMOTIONS|OTHER>",
              "priority": "<HIGH|MEDIUM|LOW>",
              "sentiment": "<POSITIVE|NEUTRAL|NEGATIVE>",
              "requiresResponse": <true|false>,
              "recommendedAction": "<REPLY|ARCHIVE|FLAG|NONE>"
            }

            Guidelines:
            - Choose REPLY only if a human response is clearly expected
            - Choose ARCHIVE for newsletters, receipts, and automated notifications
            - Choose FLAG for urgent items that need attention but no reply
            - Choose NONE for informational emails that require no action
            """)
    String analyze(@UserMessage String emailContent);
}