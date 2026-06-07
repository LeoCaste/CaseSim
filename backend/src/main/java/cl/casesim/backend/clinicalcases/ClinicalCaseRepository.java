package cl.casesim.backend.clinicalcases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicalCaseRepository extends JpaRepository<ClinicalCase, UUID> {

    List<ClinicalCase> findByActivoTrueOrderByCreadoEnDesc();

    List<ClinicalCase> findAllByOrderByCreadoEnDesc();

    Optional<ClinicalCase> findByIdAndActivoTrue(UUID id);
}
