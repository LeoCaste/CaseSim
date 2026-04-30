package cl.casesim.backend.clinicalcases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "caso_hecho_clinico")
public class ClinicalCaseFact {

    protected ClinicalCaseFact() {
        // Constructor requerido por JPA
    }

    public ClinicalCaseFact(
            UUID id,
            UUID casoId,
            String categoria,
            String nombre,
            String contenidoPaciente,
            Integer nivelRevelacion,
            String triggers,
            boolean esSensible,
            Integer orden
    ) {
        this.id = id;
        this.casoId = casoId;
        this.categoria = categoria;
        this.nombre = nombre;
        this.contenidoPaciente = contenidoPaciente;
        this.nivelRevelacion = nivelRevelacion;
        this.triggers = triggers;
        this.esSensible = esSensible;
        this.orden = orden;
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "caso_id", nullable = false)
    private UUID casoId;

    @Column(name = "categoria", nullable = false, length = 80)
    private String categoria;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "contenido_paciente", nullable = false)
    private String contenidoPaciente;

    @Column(name = "nivel_revelacion", nullable = false)
    private Integer nivelRevelacion;

    @Column(name = "triggers", columnDefinition = "jsonb")
    private String triggers;

    @Column(name = "es_sensible", nullable = false)
    private boolean esSensible;

    @Column(name = "orden")
    private Integer orden;

    public UUID getId() {
        return id;
    }

    public UUID getCasoId() {
        return casoId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getContenidoPaciente() {
        return contenidoPaciente;
    }

    public Integer getNivelRevelacion() {
        return nivelRevelacion;
    }

    public String getTriggers() {
        return triggers;
    }
}
