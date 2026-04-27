package cl.casesim.backend.sessions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sesion_simulacion")
public class SimulationSession {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "actividad_id", nullable = false)
    private UUID actividadId;

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "estado", nullable = false, length = 50)
    private String estado;

    @Column(name = "iniciada_en")
    private LocalDateTime iniciadaEn;

    @Column(name = "finalizada_en")
    private LocalDateTime finalizadaEn;

    @Column(name = "diagnostico_final")
    private String diagnosticoFinal;

    @Column(name = "razonamiento_final")
    private String razonamientoFinal;

    @Column(name = "turno_diagnostico")
    private Integer turnoDiagnostico;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    protected SimulationSession() {
        // Constructor requerido por JPA
    }

    public SimulationSession(
            UUID id,
            UUID actividadId,
            UUID estudianteId,
            String estado,
            LocalDateTime iniciadaEn,
            LocalDateTime creadaEn
    ) {
        this.id = id;
        this.actividadId = actividadId;
        this.estudianteId = estudianteId;
        this.estado = estado;
        this.iniciadaEn = iniciadaEn;
        this.creadaEn = creadaEn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActividadId() {
        return actividadId;
    }

    public UUID getEstudianteId() {
        return estudianteId;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDateTime getIniciadaEn() {
        return iniciadaEn;
    }

    public LocalDateTime getFinalizadaEn() {
        return finalizadaEn;
    }

    public String getDiagnosticoFinal() {
        return diagnosticoFinal;
    }

    public String getRazonamientoFinal() {
        return razonamientoFinal;
    }

    public Integer getTurnoDiagnostico() {
        return turnoDiagnostico;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public void completar(LocalDateTime finalizadaEn) {
        this.estado = "FINALIZADA";
        this.finalizadaEn = finalizadaEn;
    }

    public void iniciarEnCurso(LocalDateTime iniciadaEn) {
        this.estado = "EN_CURSO";
        if (this.iniciadaEn == null) {
            this.iniciadaEn = iniciadaEn;
        }
    }

    public void registrarDiagnosticoFinal(
            String diagnosticoFinal,
            String razonamientoFinal,
            Integer turnoDiagnostico,
            LocalDateTime finalizadaEn
    ) {
        this.diagnosticoFinal = diagnosticoFinal;
        this.razonamientoFinal = razonamientoFinal;
        this.turnoDiagnostico = turnoDiagnostico;
        this.estado = "FINALIZADA";
        this.finalizadaEn = finalizadaEn;
    }
}
