package com.zmail.agent.action;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface EmailSummarizeAgent {

    @SystemMessage("""
            You are an email summarizer. Analyze the provided email and respond with \
            ONLY a valid JSON object (no markdown, no explanation):
            {
              "summary": "<2-3 sentence summary of the email>",
              "actionItems": ["<action item>", ...]
            }

            Guidelines:
            - summary should be concise and capture the key information
            - Use [] for actionItems if there are none
            - actionItems should be specific, actionable tasks mentioned or implied by the email
            """)
    String summarize(@UserMessage String emailContent);
}
