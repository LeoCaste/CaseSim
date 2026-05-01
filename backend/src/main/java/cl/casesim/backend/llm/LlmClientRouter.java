package cl.casesim.backend.llm;

import java.util.List;

public class LlmClientRouter implements LlmClient {

    private final LlmProperties llmProperties;
    private final LlmClient defaultClient;
    private final LlmClient groqClient;

    public LlmClientRouter(LlmProperties llmProperties, LlmClient defaultClient, LlmClient groqClient) {
        this.llmProperties = llmProperties;
        this.defaultClient = defaultClient;
        this.groqClient = groqClient;
    }

    @Override
    public String generateChatCompletion(List<ChatPromptMessage> messages) {
        return resolveClient().generateChatCompletion(messages);
    }

    @Override
    public String generateChatCompletion(List<ChatPromptMessage> messages, Double temperature, Integer maxTokens) {
        return resolveClient().generateChatCompletion(messages, temperature, maxTokens);
    }

    private LlmClient resolveClient() {
        String provider = LlmProviderSupport.normalize(llmProperties.getProvider());
        if (LlmProviderSupport.GROQ.equals(provider)) {
            return groqClient;
        }
        return defaultClient;
    }
}
