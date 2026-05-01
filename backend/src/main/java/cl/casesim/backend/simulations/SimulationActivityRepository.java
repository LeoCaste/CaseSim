package cl.casesim.backend.simulations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SimulationActivityRepository extends JpaRepository<SimulationActivity, UUID> {

    boolean existsByCasoIdAndCreadaPor(UUID casoId, UUID creadaPor);

    void deleteByCasoId(UUID casoId);
}
