package cl.casesim.backend.llm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LlmUsageRepository extends JpaRepository<LlmUsage, UUID> {

    @Query(value = """
            select
              cast(creado_en as date) as usageDate,
              coalesce(sum(prompt_tokens), 0) as tokensInput,
              coalesce(sum(completion_tokens), 0) as tokensOutput,
              count(*) as calls,
              avg(latencia_ms) as avgLatencyMs
            from uso_llm
            group by cast(creado_en as date)
            order by cast(creado_en as date) desc
            """, nativeQuery = true)
    List<LlmUsageDailyProjection> findDailyUsage();

    @Query(value = """
            select
              count(*) as totalCalls,
              coalesce(sum(total_tokens), 0) as totalTokens,
              avg(latencia_ms) as avgLatencyMs,
              coalesce(sum(case when fallback_usado then 1 else 0 end), 0) as fallbackCount,
              coalesce(sum(case when error_detalle is not null and trim(error_detalle) <> '' then 1 else 0 end), 0) as errorCount
            from uso_llm
            """, nativeQuery = true)
    LlmUsageSummaryProjection getSummary();
}
