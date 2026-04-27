package cl.casesim.backend.llm;

import java.util.concurrent.atomic.AtomicLong;

public class LlmUsageService {

    private final AtomicLong llmCalls = new AtomicLong(0);
    private final AtomicLong fallbackCalls = new AtomicLong(0);

    public void markLlmCall() {
        llmCalls.incrementAndGet();
    }

    public void markFallbackCall() {
        fallbackCalls.incrementAndGet();
    }

    public long getLlmCalls() {
        return llmCalls.get();
    }

    public long getFallbackCalls() {
        return fallbackCalls.get();
    }
}
