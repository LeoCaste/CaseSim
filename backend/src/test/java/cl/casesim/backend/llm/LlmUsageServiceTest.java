package cl.casesim.backend.llm;

import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.llm.dto.LlmSummaryResponse;
import cl.casesim.backend.llm.dto.LlmUsageDailyResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmUsageServiceTest {

    private final LlmUsageRepository llmUsageRepository = mock(LlmUsageRepository.class);
    private final LlmUsageService service = new LlmUsageService(
            llmUsageRepository,
            new BigDecimal("0.00015"),
            new BigDecimal("0.00060"),
            new BigDecimal("950")
    );

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
        when(summaryRow.getTotalPromptTokens()).thenReturn(600L);
        when(summaryRow.getTotalCompletionTokens()).thenReturn(400L);
        when(summaryRow.getAvgLatencyMs()).thenReturn(280.0);
        when(summaryRow.getFallbackCount()).thenReturn(3L);
        when(summaryRow.getErrorCount()).thenReturn(2L);
        when(llmUsageRepository.getSummary()).thenReturn(summaryRow);

        LlmSummaryResponse summary = service.getSummary();

        assertEquals(10L, summary.totalCalls());
        assertEquals(1000L, summary.totalTokens());
        assertEquals(3L, summary.fallbackCount());
        assertEquals(2L, summary.errorCount());
        assertEquals(new BigDecimal("0.000330"), summary.estimatedCostUsd());
        assertEquals(new BigDecimal("0.31"), summary.estimatedCostClp());
        assertEquals(new BigDecimal("950.0000"), summary.usdToClpRate());
    }

    @Test
    void getDailyUsageShouldApplyFiltersWhenProvided() {
        LlmUsageDailyProjection row = mock(LlmUsageDailyProjection.class);
        when(row.getUsageDate()).thenReturn(LocalDate.of(2026, 4, 27));
        when(row.getTokensInput()).thenReturn(120L);
        when(row.getTokensOutput()).thenReturn(80L);
        when(row.getCalls()).thenReturn(5L);
        when(row.getAvgLatencyMs()).thenReturn(350.0);
        when(llmUsageRepository.findDailyUsageFiltered(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "gpt-4o-mini",
                "error"
        )).thenReturn(List.of(row));

        List<LlmUsageDailyResponse> usage = service.getDailyUsage(
                "2026-04-01",
                "2026-04-30",
                " GPT-4O-MINI ",
                " error "
        );

        assertEquals(1, usage.size());
        assertEquals(LocalDate.of(2026, 4, 27), usage.getFirst().date());
        verify(llmUsageRepository).findDailyUsageFiltered(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "gpt-4o-mini",
                "error"
        );
        verify(llmUsageRepository, never()).findDailyUsage();
    }

    @Test
    void getSummaryShouldApplyFallbackFilterWhenProvided() {
        LlmUsageSummaryProjection summaryRow = mock(LlmUsageSummaryProjection.class);
        when(summaryRow.getTotalCalls()).thenReturn(4L);
        when(summaryRow.getTotalTokens()).thenReturn(400L);
        when(summaryRow.getTotalPromptTokens()).thenReturn(100L);
        when(summaryRow.getTotalCompletionTokens()).thenReturn(300L);
        when(summaryRow.getAvgLatencyMs()).thenReturn(250.0);
        when(summaryRow.getFallbackCount()).thenReturn(4L);
        when(summaryRow.getErrorCount()).thenReturn(0L);
        when(llmUsageRepository.getSummaryFiltered(
                LocalDate.of(2026, 4, 1),
                null,
                null,
                "fallback"
        )).thenReturn(summaryRow);

        LlmSummaryResponse summary = service.getSummary("2026-04-01", null, null, "fallback");

        assertEquals(4L, summary.totalCalls());
        assertEquals(400L, summary.totalTokens());
        assertEquals(4L, summary.fallbackCount());
        assertEquals(new BigDecimal("0.000195"), summary.estimatedCostUsd());
        assertEquals(new BigDecimal("0.19"), summary.estimatedCostClp());
        verify(llmUsageRepository).getSummaryFiltered(
                LocalDate.of(2026, 4, 1),
                null,
                null,
                "fallback"
        );
        verify(llmUsageRepository, never()).getSummary();
    }

    @Test
    void getSummaryShouldUseDefaultEconomicValuesWhenConfiguredValuesAreInvalid() {
        LlmUsageService serviceWithInvalidConfig = new LlmUsageService(
                llmUsageRepository,
                BigDecimal.ZERO,
                new BigDecimal("-1"),
                null
        );

        LlmUsageSummaryProjection summaryRow = mock(LlmUsageSummaryProjection.class);
        when(summaryRow.getTotalCalls()).thenReturn(2L);
        when(summaryRow.getTotalTokens()).thenReturn(2000L);
        when(summaryRow.getTotalPromptTokens()).thenReturn(1000L);
        when(summaryRow.getTotalCompletionTokens()).thenReturn(1000L);
        when(summaryRow.getAvgLatencyMs()).thenReturn(180.0);
        when(summaryRow.getFallbackCount()).thenReturn(0L);
        when(summaryRow.getErrorCount()).thenReturn(0L);
        when(llmUsageRepository.getSummary()).thenReturn(summaryRow);

        LlmSummaryResponse summary = serviceWithInvalidConfig.getSummary();

        assertEquals(new BigDecimal("0.000750"), summary.estimatedCostUsd());
        assertEquals(new BigDecimal("0.71"), summary.estimatedCostClp());
        assertEquals(new BigDecimal("950.0000"), summary.usdToClpRate());
    }

    @Test
    void getDailyUsageShouldKeepDefaultBehaviorWhenFiltersAreBlankOrAll() {
        when(llmUsageRepository.findDailyUsage()).thenReturn(List.of());

        service.getDailyUsage(null, " ", "   ", " all ");

        verify(llmUsageRepository).findDailyUsage();
        verify(llmUsageRepository, never()).findDailyUsageFiltered(any(), any(), any(), any());
    }

    @Test
    void getSummaryShouldFailWhenDateRangeIsInvalid() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.getSummary("2026-04-30", "2026-04-01", null, null)
        );

        assertEquals("El parámetro from no puede ser posterior a to.", exception.getMessage());
    }

    @Test
    void getDailyUsageShouldFailWhenStatusIsInvalid() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.getDailyUsage(null, null, null, "partial")
        );

        assertEquals("El parámetro status debe ser all, error o fallback.", exception.getMessage());
    }

    @Test
    void getSummaryShouldFailWhenFromDateFormatIsInvalid() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.getSummary("04-01-2026", null, null, null)
        );

        assertEquals("El parámetro from debe tener formato yyyy-MM-dd.", exception.getMessage());
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
