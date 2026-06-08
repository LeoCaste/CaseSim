package cl.casesim.backend.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<AppUser, UUID> {

    @EntityGraph(attributePaths = "roles")
    List<AppUser> findAllByOrderByNombreAsc();

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByEmailIgnoreCaseAndActivoTrue(String email);

    @EntityGraph(attributePaths = "roles")
    @Query("""
            select distinct u
            from AppUser u
            left join u.roles r
            where u.activo = true
              and (
                  upper(r.nombre) = 'ESTUDIANTE'
                  or exists (
                      select 1
                      from CourseEnrollment ce
                      where ce.usuarioId = u.id
                        and ce.rolEnCurso = 'ESTUDIANTE'
                  )
              )
            order by u.nombre asc
            """)
    List<AppUser> findAllStudentsOrderByNombreAsc();

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @Query("""
            select (count(u) > 0)
            from AppUser u
            join u.roles r
            where u.activo = true
              and upper(r.nombre) = 'ADMIN'
            """)
    boolean existsActiveAdmin();

    @Query("""
            select (count(u) > 0)
            from AppUser u
            join u.roles r
            where upper(r.nombre) = 'ADMIN'
            """)
    boolean existsAdmin();
}
