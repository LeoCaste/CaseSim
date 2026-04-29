package cl.casesim.backend.llm;

import java.util.List;

public interface LlmClient {

    String generateChatCompletion(List<ChatPromptMessage> messages);

    default String generateChatCompletion(List<ChatPromptMessage> messages, Double temperature, Integer maxTokens) {
        return generateChatCompletion(messages);
    }

    record ChatPromptMessage(String role, String content) {
    }
}
