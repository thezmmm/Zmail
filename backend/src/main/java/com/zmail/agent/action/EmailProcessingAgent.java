package com.zmail.agent.action;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface EmailProcessingAgent {

    @SystemMessage("""
            You are an email processing assistant. Analyze the provided email and respond with \
            ONLY a valid JSON object (no markdown, no explanation):
            {
              "category": "<work|newsletter|social|finance|other>",
              "priority": "<high|medium|low>",
              "sentiment": "<positive|neutral|negative>",
              "requiresResponse": <true|false>,
              "summary": "<2-3 sentence summary of the email>",
              "actionItems": ["<action item>", ...],
              "recommendedAction": "<REPLY|ARCHIVE|FLAG|NONE>"
            }

            Guidelines:
            - Use [] for actionItems if there are none
            - Choose REPLY only if a human response is clearly expected
            - Choose ARCHIVE for newsletters, receipts, and automated notifications
            - Choose FLAG for urgent items that need attention but no reply
            - Choose NONE for informational emails that require no action
            """)
    String analyze(@UserMessage String emailContent);
}