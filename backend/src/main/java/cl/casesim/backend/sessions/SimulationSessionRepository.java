package cl.casesim.backend.sessions;

import cl.casesim.backend.professor.ProfessorSessionProjection;
import cl.casesim.backend.simulations.StudentActivityProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulationSessionRepository extends JpaRepository<SimulationSession, UUID> {

    Optional<SimulationSession> findByIdAndEstudianteId(UUID id, UUID estudianteId);

    Optional<SimulationSession> findByActividadIdAndEstudianteId(UUID actividadId, UUID estudianteId);

    @Query("""
            select distinct s.estudianteId
            from SimulationSession s
            join SimulationActivity a on a.id = s.actividadId
            where a.casoId = :clinicalCaseId
              and s.estudianteId in :studentIds
            """)
    List<UUID> findAssignedStudentIdsByClinicalCaseId(
            @Param("clinicalCaseId") UUID clinicalCaseId,
            @Param("studentIds") Collection<UUID> studentIds
    );

    @Query("""
            select
                s.actividadId as activityId,
                a.casoId as clinicalCaseId,
                a.titulo as title,
                s.estado as status
            from SimulationSession s
            join SimulationActivity a on a.id = s.actividadId
            where s.estudianteId = :studentId
            order by s.creadaEn desc
            """)
    List<StudentActivityProjection> findStudentActivitiesByStudentId(@Param("studentId") UUID studentId);

    @Query("""
            select s
            from SimulationSession s
            join SimulationActivity a on a.id = s.actividadId
            join ClinicalCase c on c.id = a.casoId
            where a.creadaPor = :professorId
               or c.creadoPor = :professorId
            order by s.creadaEn desc
            """)
    List<SimulationSession> findProfessorVisibleSessions(
            @Param("professorId") UUID professorId,
            Pageable pageable
    );

    @Query("""
            select s
            from SimulationSession s
            join SimulationActivity a on a.id = s.actividadId
            join ClinicalCase c on c.id = a.casoId
            where s.id = :sessionId
              and (a.creadaPor = :professorId or c.creadoPor = :professorId)
            """)
    Optional<SimulationSession> findProfessorVisibleSessionById(
            @Param("sessionId") UUID sessionId,
            @Param("professorId") UUID professorId
    );

    @Query("""
            select
                s.id as sessionId,
                u.nombre as studentName,
                a.titulo as activityName,
                c.titulo as caseName,
                s.estado as status,
                s.iniciadaEn as startedAt,
                s.finalizadaEn as finishedAt,
                s.diagnosticoFinal as finalDiagnosis,
                s.razonamientoFinal as finalReasoning,
                coalesce((select max(m.numeroTurno) from ChatMessage m where m.sesionId = s.id), 0) as turns
            from SimulationSession s
            join SimulationActivity a on a.id = s.actividadId
            join ClinicalCase c on c.id = a.casoId
            join AppUser u on u.id = s.estudianteId
            where a.creadaPor = :professorId
               or c.creadoPor = :professorId
            order by coalesce(s.finalizadaEn, s.iniciadaEn, s.creadaEn) desc
            """)
    List<ProfessorSessionProjection> findProfessorSessionSummaries(
            @Param("professorId") UUID professorId,
            Pageable pageable
    );

    @Query("""
            select
                s.id as sessionId,
                u.nombre as studentName,
                a.titulo as activityName,
                c.titulo as caseName,
                s.estado as status,
                s.iniciadaEn as startedAt,
                s.finalizadaEn as finishedAt,
                s.diagnosticoFinal as finalDiagnosis,
                s.razonamientoFinal as finalReasoning,
                coalesce((select max(m.numeroTurno) from ChatMessage m where m.sesionId = s.id), 0) as turns
            from SimulationSession s
            join SimulationActivity a on a.id = s.actividadId
            join ClinicalCase c on c.id = a.casoId
            join AppUser u on u.id = s.estudianteId
            where s.id = :sessionId
              and (a.creadaPor = :professorId or c.creadoPor = :professorId)
            """)
    Optional<ProfessorSessionProjection> findProfessorSessionSummaryById(
            @Param("sessionId") UUID sessionId,
            @Param("professorId") UUID professorId
    );
}
