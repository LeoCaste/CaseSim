package cl.casesim.backend.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private boolean enabled = false;
    private String provider = "openai";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private String baseUrl = "https://api.openai.com/v1/chat/completions";
    private int timeoutMs = 30000;
    private int maxRetries = 1;
    private double temperature = 0.4;
    private int maxTokens = 350;
    private int historyTurns = 6;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getHistoryTurns() {
        return historyTurns;
    }

    public void setHistoryTurns(int historyTurns) {
        this.historyTurns = historyTurns;
    }

    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }
}
