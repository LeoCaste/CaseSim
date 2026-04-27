package cl.casesim.backend.sessions;

import cl.casesim.backend.simulations.StudentActivityProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulationSessionRepository extends JpaRepository<SimulationSession, UUID> {

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
}
