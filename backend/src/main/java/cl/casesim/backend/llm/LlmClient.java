package cl.casesim.backend.llm;

import java.util.List;

public interface LlmClient {

    String generateChatCompletion(List<ChatPromptMessage> messages);

    record ChatPromptMessage(String role, String content) {
    }
}
