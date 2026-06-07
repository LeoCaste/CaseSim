package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:llmusagetest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class LlmUsageRepositoryTest {

    @Autowired
    private LlmUsageRepository repository;

    @Test
    void summaryAndStatusFiltersShouldKeepFallbackAndErrorExclusive() {
        UUID sessionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        repository.save(new LlmUsage(UUID.randomUUID(), sessionId, "openai", "gpt-4o-mini", 100, 50, 150, 210, false, null, now));
        repository.save(new LlmUsage(UUID.randomUUID(), sessionId, "openai", "gpt-4o-mini", 80, 30, 110, 190, false, null, now));
        repository.save(new LlmUsage(UUID.randomUUID(), sessionId, "openai", "gpt-4o-mini", 40, 20, 60, 170, true, "PROVIDER_CALL_ERROR|TIMEOUT", now));
        repository.save(new LlmUsage(UUID.randomUUID(), sessionId, "openai", "gpt-4o-mini", 20, 10, 30, 160, false, "PROVIDER_HTTP_500", now));
        repository.save(new LlmUsage(UUID.randomUUID(), null, "openai", "gpt-4o-mini", 5, 3, 8, 155, false, null, now));

        LlmUsageSummaryProjection summary = repository.getSummary();
        assertEquals(5L, summary.getTotalCalls());
        assertEquals(1L, summary.getFallbackCount());
        assertEquals(1L, summary.getErrorCount());

        LlmUsageSummaryProjection fallbackSummary = repository.getSummaryFiltered(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                "gpt-4o-mini",
                "fallback"
        );
        assertEquals(1L, fallbackSummary.getTotalCalls());
        assertEquals(1L, fallbackSummary.getFallbackCount());
        assertEquals(0L, fallbackSummary.getErrorCount());

        LlmUsageSummaryProjection errorSummary = repository.getSummaryFiltered(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                "gpt-4o-mini",
                "error"
        );
        assertEquals(1L, errorSummary.getTotalCalls());
        assertEquals(0L, errorSummary.getFallbackCount());
        assertEquals(1L, errorSummary.getErrorCount());

        List<LlmUsageDailyProjection> fallbackDaily = repository.findDailyUsageFiltered(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                "gpt-4o-mini",
                "fallback"
        );
        assertEquals(1L, fallbackDaily.getFirst().getCalls());

        List<LlmUsageDailyProjection> errorDaily = repository.findDailyUsageFiltered(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                "gpt-4o-mini",
                "error"
        );
        assertEquals(1L, errorDaily.getFirst().getCalls());
    }
}
