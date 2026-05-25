package cl.casesim.backend.llm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LlmConfigRepository extends JpaRepository<LlmConfig, UUID> {
    Optional<LlmConfig> findFirstByOrderByUpdatedAtDesc();
}
