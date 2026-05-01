package cl.casesim.backend.llm;

public interface LlmClient {

    String providerType();

    LlmResponse generate(LlmRequest request);

    default String generateChatCompletion(java.util.List<LlmMessage> messages, Double temperature, Integer maxTokens) {
        LlmRequest request = new LlmRequest(messages, null, temperature, maxTokens);
        LlmResponse response = generate(request);
        return response == null ? "" : response.content();
    }
}
