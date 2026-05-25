package cl.casesim.backend.llm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LlmUsageRepository extends JpaRepository<LlmUsage, UUID> {

    @Query(value = """
            select
              cast(creado_en as date) as usageDate,
              proveedor as provider,
              modelo as model,
              case
                when fallback_usado = true then 'fallback'
                when error_detalle is not null and trim(error_detalle) <> '' then 'error'
                else 'success'
              end as status,
              coalesce(sum(prompt_tokens), 0) as tokensInput,
              coalesce(sum(completion_tokens), 0) as tokensOutput,
              count(*) as calls,
              avg(latencia_ms) as avgLatencyMs,
              case
                when bool_and(fallback_usado = false and (error_detalle is null or trim(error_detalle) = '')) then false
                else true
              end as tokenEstimated,
              case
                when bool_and(fallback_usado = false and (error_detalle is null or trim(error_detalle) = '')) then 'real'
                else 'estimated'
              end as tokenSource
            from uso_llm
            group by cast(creado_en as date), proveedor, modelo,
                     case
                       when fallback_usado = true then 'fallback'
                       when error_detalle is not null and trim(error_detalle) <> '' then 'error'
                       else 'success'
                     end
            order by cast(creado_en as date) desc, provider asc, model asc
            """, nativeQuery = true)
    List<LlmUsageDailyProjection> findDailyUsage();

    @Query(value = """
            select
              cast(creado_en as date) as usageDate,
              proveedor as provider,
              modelo as model,
              case
                when fallback_usado = true then 'fallback'
                when error_detalle is not null and trim(error_detalle) <> '' then 'error'
                else 'success'
              end as status,
              coalesce(sum(prompt_tokens), 0) as tokensInput,
              coalesce(sum(completion_tokens), 0) as tokensOutput,
              count(*) as calls,
              avg(latencia_ms) as avgLatencyMs,
              case
                when bool_and(fallback_usado = false and (error_detalle is null or trim(error_detalle) = '')) then false
                else true
              end as tokenEstimated,
              case
                when bool_and(fallback_usado = false and (error_detalle is null or trim(error_detalle) = '')) then 'real'
                else 'estimated'
              end as tokenSource
            from uso_llm
            where (:fromDate is null or cast(creado_en as date) >= :fromDate)
              and (:toDate is null or cast(creado_en as date) <= :toDate)
              and (:model is null or lower(modelo) = :model)
              and (
                :status = 'all'
                or (:status = 'error' and fallback_usado = false and error_detalle is not null and trim(error_detalle) <> '')
                or (:status = 'fallback' and fallback_usado = true)
              )
            group by cast(creado_en as date), proveedor, modelo,
                     case
                       when fallback_usado = true then 'fallback'
                       when error_detalle is not null and trim(error_detalle) <> '' then 'error'
                       else 'success'
                     end
            order by cast(creado_en as date) desc, provider asc, model asc
            """, nativeQuery = true)
    List<LlmUsageDailyProjection> findDailyUsageFiltered(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("model") String model,
            @Param("status") String status
    );

    @Query(value = """
            select
              count(*) as totalCalls,
              coalesce(sum(total_tokens), 0) as totalTokens,
              coalesce(sum(prompt_tokens), 0) as totalPromptTokens,
              coalesce(sum(completion_tokens), 0) as totalCompletionTokens,
              avg(latencia_ms) as avgLatencyMs,
              coalesce(sum(case when fallback_usado then 1 else 0 end), 0) as fallbackCount,
              coalesce(sum(case when fallback_usado = false and error_detalle is not null and trim(error_detalle) <> '' then 1 else 0 end), 0) as errorCount
            from uso_llm
            """, nativeQuery = true)
    LlmUsageSummaryProjection getSummary();

    @Query(value = """
            select
              count(*) as totalCalls,
              coalesce(sum(total_tokens), 0) as totalTokens,
              coalesce(sum(prompt_tokens), 0) as totalPromptTokens,
              coalesce(sum(completion_tokens), 0) as totalCompletionTokens,
              avg(latencia_ms) as avgLatencyMs,
              coalesce(sum(case when fallback_usado then 1 else 0 end), 0) as fallbackCount,
              coalesce(sum(case when fallback_usado = false and error_detalle is not null and trim(error_detalle) <> '' then 1 else 0 end), 0) as errorCount
            from uso_llm
            where (:fromDate is null or cast(creado_en as date) >= :fromDate)
              and (:toDate is null or cast(creado_en as date) <= :toDate)
              and (:model is null or lower(modelo) = :model)
              and (
                :status = 'all'
                or (:status = 'error' and fallback_usado = false and error_detalle is not null and trim(error_detalle) <> '')
                or (:status = 'fallback' and fallback_usado = true)
              )
            """, nativeQuery = true)
    LlmUsageSummaryProjection getSummaryFiltered(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("model") String model,
            @Param("status") String status
    );
}
