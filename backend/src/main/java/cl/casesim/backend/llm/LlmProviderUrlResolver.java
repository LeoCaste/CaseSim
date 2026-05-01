package cl.casesim.backend.llm;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

public class LlmProviderUrlResolver {

    private static final String OPENAI_DEFAULT = "https://api.openai.com/v1/chat/completions";
    private static final String GROQ_DEFAULT = "https://api.groq.com/openai/v1/chat/completions";

    public String resolve(String provider, String configuredBaseUrl) {
        String normalizedProvider = LlmProviderSupport.normalize(provider);
        String configured = StringUtils.hasText(configuredBaseUrl) ? configuredBaseUrl.trim() : "";
        if (LlmProviderSupport.GROQ.equals(normalizedProvider)) {
            return normalizeGroq(configured);
        }
        if (LlmProviderSupport.OPENAI.equals(normalizedProvider)) {
            return normalizeOpenAi(configured);
        }
        return StringUtils.hasText(configured) ? configured : LlmProviderSupport.defaultBaseUrl(normalizedProvider);
    }

    String normalizeOpenAi(String configured) {
        if (!StringUtils.hasText(configured)) {
            return OPENAI_DEFAULT;
        }
        return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
    }

    String normalizeGroq(String configured) {
        String source = StringUtils.hasText(configured) ? configured.trim() : GROQ_DEFAULT;
        try {
            URI uri = new URI(source);
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+", "/");
            if (!path.endsWith("/openai/v1/chat/completions")) {
                if (path.endsWith("/v1/chat/completions")) {
                    path = path.substring(0, path.length() - "/v1/chat/completions".length()) + "/openai/v1/chat/completions";
                } else if (path.endsWith("/openai")) {
                    path = path + "/v1/chat/completions";
                } else if (path.endsWith("/v1")) {
                    path = path + "/chat/completions";
                } else {
                    path = (path.endsWith("/") ? path.substring(0, path.length() - 1) : path) + "/openai/v1/chat/completions";
                }
            }
            URI normalized = new URI(uri.getScheme(), uri.getAuthority(), path, uri.getQuery(), uri.getFragment());
            return normalized.toString();
        } catch (URISyntaxException ex) {
            return GROQ_DEFAULT;
        }
    }
}
