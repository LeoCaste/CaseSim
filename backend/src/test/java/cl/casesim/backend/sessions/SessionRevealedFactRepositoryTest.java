package cl.casesim.backend.sessions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SessionRevealedFactRepositoryTest {

    @Autowired
    private SessionRevealedFactRepository sessionRevealedFactRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistenciaSesionHechoReveladoFuncionaYEvitaDuplicadosPorSesionYFact() {
        UUID sessionId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        setupRequiredRows(sessionId, factId);

        SessionRevealedFact first = new SessionRevealedFact(
                UUID.randomUUID(),
                sessionId,
                factId,
                LocalDateTime.now()
        );

        sessionRevealedFactRepository.saveAndFlush(first);

        assertEquals(1, sessionRevealedFactRepository.findBySessionId(sessionId).size());
        assertEquals(Set.of(factId), sessionRevealedFactRepository.findFactIdsBySessionId(sessionId));

        SessionRevealedFact duplicated = new SessionRevealedFact(
                UUID.randomUUID(),
                sessionId,
                factId,
                LocalDateTime.now()
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> sessionRevealedFactRepository.saveAndFlush(duplicated)
        );
    }

    private void setupRequiredRows(UUID sessionId, UUID factId) {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID clinicalCaseId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        jdbcTemplate.update(
                "insert into institucion (id, nombre, activo, creado_en) values (?, ?, ?, now())",
                institutionId,
                "Institución test",
                true
        );

        jdbcTemplate.update(
                "insert into usuario (id, nombre, email, password_hash, activo, creado_en) values (?, ?, ?, ?, ?, now())",
                userId,
                "Estudiante test",
                "estudiante+" + userId + "@ufromail.cl",
                "hash",
                true
        );

        jdbcTemplate.update(
                "insert into curso (id, institucion_id, nombre, activo, creado_en) values (?, ?, ?, ?, now())",
                courseId,
                institutionId,
                "Curso test",
                true
        );

        jdbcTemplate.update(
                "insert into caso_clinico (id, titulo, motivo_consulta, frase_sin_informacion, activo, creado_en) values (?, ?, ?, ?, ?, now())",
                clinicalCaseId,
                "Caso test",
                "Dolor abdominal",
                "No tengo información asociada a eso.",
                true
        );

        jdbcTemplate.update(
                "insert into actividad_simulacion (id, curso_id, caso_id, titulo, modo, usa_tiempo, activa, creada_en) values (?, ?, ?, ?, ?, ?, ?, now())",
                activityId,
                courseId,
                clinicalCaseId,
                "Actividad test",
                "FORMATIVO",
                false,
                true
        );

        jdbcTemplate.update(
                "insert into sesion_simulacion (id, actividad_id, estudiante_id, estado, creada_en) values (?, ?, ?, ?, now())",
                sessionId,
                activityId,
                userId,
                "EN_CURSO"
        );

        jdbcTemplate.update(
                "insert into caso_hecho_clinico (id, caso_id, categoria, nombre, contenido_paciente, nivel_revelacion, es_sensible, orden) values (?, ?, ?, ?, ?, ?, ?, ?)",
                factId,
                clinicalCaseId,
                "GENERAL",
                "fiebre",
                "Tengo fiebre",
                2,
                false,
                0
        );
    }
}
