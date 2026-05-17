package com.zmail.agent.chat;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ConversationSummaryAgent {

    @SystemMessage("""
            You are a conversation summarizer. Your job is to produce a concise summary of a \
            chat conversation between a user and an email assistant called Zmail.

            Rules:
            - Keep the summary under 300 words
            - Preserve key facts: emails discussed, decisions made, drafts requested, questions answered
            - If a previous summary is provided, merge it with the new messages into one coherent summary
            - Write in third-person past tense (e.g. "The user asked about...", "Zmail drafted...")
            - Do not include filler phrases like "In this conversation..."
            """)
    String summarize(@UserMessage String conversationText);
}