package cl.casesim.backend.sessions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sessionrepo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class SessionRevealedFactRepositoryTest {

    @Autowired
    private SessionRevealedFactRepository sessionRevealedFactRepository;

    @Test
    void persistenciaSesionHechoReveladoFuncionaYEvitaDuplicadosPorSesionYFact() {
        UUID sessionId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();

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
}
