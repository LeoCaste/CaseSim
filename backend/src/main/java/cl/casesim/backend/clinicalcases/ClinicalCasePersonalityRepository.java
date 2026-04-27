package cl.casesim.backend.clinicalcases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClinicalCasePersonalityRepository extends JpaRepository<ClinicalCasePersonality, UUID> {

    List<ClinicalCasePersonality> findByCasoId(UUID casoId);

    void deleteByCasoId(UUID casoId);
}
