package cl.casesim.backend.clinicalcases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "caso_personalidad")
public class ClinicalCasePersonality {

    protected ClinicalCasePersonality() {
        // Constructor requerido por JPA
    }

    public ClinicalCasePersonality(UUID id, UUID casoId, String rasgo, String descripcion) {
        this.id = id;
        this.casoId = casoId;
        this.rasgo = rasgo;
        this.descripcion = descripcion;
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "caso_id", nullable = false)
    private UUID casoId;

    @Column(name = "rasgo", nullable = false, length = 120)
    private String rasgo;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    public UUID getCasoId() {
        return casoId;
    }

    public String getRasgo() {
        return rasgo;
    }
}
