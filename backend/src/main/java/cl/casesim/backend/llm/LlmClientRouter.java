package cl.casesim.backend.llm;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LlmClientRouter implements LlmClient {

    private final LlmProperties llmProperties;
    private final Map<String, LlmClient> clientsByProvider;

    public LlmClientRouter(LlmProperties llmProperties, java.util.List<LlmClient> clients) {
        this.llmProperties = llmProperties;
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(c -> LlmProviderSupport.normalize(c.providerType()), Function.identity()));
    }

    @Override
    public String providerType() {
        return "router";
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        return resolveClient().generate(request);
    }

    private LlmClient resolveClient() {
        String provider = LlmProviderSupport.normalize(llmProperties.getProvider());
        if (LlmProviderSupport.OPENAI_COMPATIBLE.equals(provider)) {
            provider = LlmProviderSupport.OPENAI;
        }
        LlmClient client = clientsByProvider.get(provider);
        if (client == null) {
            throw new LlmClientException("Proveedor no soportado en router: " + provider,
                    null,
                    new LlmProviderError(LlmErrorCategory.INVALID_REQUEST, null, "provider=" + provider));
        }
        return client;
    }
}
