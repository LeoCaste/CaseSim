package cl.casesim.backend.simulations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, UUID> {

    @Query("""
            select ce.cursoId
            from CourseEnrollment ce
            where ce.usuarioId in :studentIds
              and ce.rolEnCurso = 'ESTUDIANTE'
            group by ce.cursoId
            having count(distinct ce.usuarioId) = :studentCount
            """)
    List<UUID> findSharedCourseIdsForStudents(
            @Param("studentIds") Collection<UUID> studentIds,
            @Param("studentCount") long studentCount
    );
}
