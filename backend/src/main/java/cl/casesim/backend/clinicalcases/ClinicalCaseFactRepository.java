package cl.casesim.backend.clinicalcases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClinicalCaseFactRepository extends JpaRepository<ClinicalCaseFact, UUID> {

    List<ClinicalCaseFact> findByCasoIdOrderByOrdenAsc(UUID casoId);

    void deleteByCasoId(UUID casoId);
}
