package cl.casesim.backend.llm;

import cl.casesim.backend.llm.dto.LlmSummaryResponse;
import cl.casesim.backend.llm.dto.LlmUsageDailyResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmUsageServiceTest {

    private final LlmUsageRepository llmUsageRepository = mock(LlmUsageRepository.class);
    private final LlmUsageService service = new LlmUsageService(llmUsageRepository);

    @Test
    void getDailyUsageShouldMapRepositoryProjection() {
        LlmUsageDailyProjection row = mock(LlmUsageDailyProjection.class);
        when(row.getUsageDate()).thenReturn(LocalDate.of(2026, 4, 27));
        when(row.getTokensInput()).thenReturn(120L);
        when(row.getTokensOutput()).thenReturn(80L);
        when(row.getCalls()).thenReturn(5L);
        when(row.getAvgLatencyMs()).thenReturn(350.0);
        when(llmUsageRepository.findDailyUsage()).thenReturn(List.of(row));

        List<LlmUsageDailyResponse> usage = service.getDailyUsage();

        assertEquals(1, usage.size());
        assertEquals(120L, usage.getFirst().tokensInput());
        assertEquals(80L, usage.getFirst().tokensOutput());
        assertEquals(5L, usage.getFirst().calls());
    }

    @Test
    void getSummaryShouldMapRepositoryProjection() {
        LlmUsageSummaryProjection summaryRow = mock(LlmUsageSummaryProjection.class);
        when(summaryRow.getTotalCalls()).thenReturn(10L);
        when(summaryRow.getTotalTokens()).thenReturn(1000L);
        when(summaryRow.getAvgLatencyMs()).thenReturn(280.0);
        when(summaryRow.getFallbackCount()).thenReturn(3L);
        when(summaryRow.getErrorCount()).thenReturn(2L);
        when(llmUsageRepository.getSummary()).thenReturn(summaryRow);

        LlmSummaryResponse summary = service.getSummary();

        assertEquals(10L, summary.totalCalls());
        assertEquals(1000L, summary.totalTokens());
        assertEquals(3L, summary.fallbackCount());
        assertEquals(2L, summary.errorCount());
    }

    @Test
    void registerCallShouldNormalizeProviderAndModelToMatchSchemaLimits() {
        when(llmUsageRepository.save(any(LlmUsage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String provider = "   ";
        String model = "m".repeat(120);

        service.registerCall(
                UUID.randomUUID(),
                provider,
                model,
                10,
                5,
                120,
                false,
                null
        );

        ArgumentCaptor<LlmUsage> captor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository).save(captor.capture());

        LlmUsage saved = captor.getValue();
        assertEquals("unknown", ReflectionTestUtils.getField(saved, "provider"));
        assertEquals(100, ((String) ReflectionTestUtils.getField(saved, "model")).length());
    }
}
