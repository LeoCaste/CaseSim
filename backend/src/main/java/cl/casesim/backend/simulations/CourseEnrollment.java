package cl.casesim.backend.simulations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "curso_usuario")
public class CourseEnrollment {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "curso_id", nullable = false)
    private UUID cursoId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "rol_en_curso", nullable = false, length = 50)
    private String rolEnCurso;

    protected CourseEnrollment() {
        // Constructor requerido por JPA
    }

    public UUID getCursoId() {
        return cursoId;
    }
}
