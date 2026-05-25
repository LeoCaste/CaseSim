package cl.casesim.backend.simulations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "actividad_simulacion")
public class SimulationActivity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "curso_id", nullable = false)
    private UUID cursoId;

    @Column(name = "caso_id", nullable = false)
    private UUID casoId;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "modo", nullable = false, length = 50)
    private String modo;

    @Column(name = "usa_tiempo", nullable = false)
    private boolean usaTiempo;

    @Column(name = "tiempo_limite_minutos")
    private Integer tiempoLimiteMinutos;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    @Column(name = "creada_por")
    private UUID creadaPor;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    protected SimulationActivity() {
        // Constructor requerido por JPA
    }

    public SimulationActivity(
            UUID id,
            UUID cursoId,
            UUID casoId,
            String titulo,
            String descripcion,
            String modo,
            boolean usaTiempo,
            Integer tiempoLimiteMinutos,
            boolean activa,
            UUID creadaPor,
            LocalDateTime creadaEn
    ) {
        this.id = id;
        this.cursoId = cursoId;
        this.casoId = casoId;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.modo = modo;
        this.usaTiempo = usaTiempo;
        this.tiempoLimiteMinutos = tiempoLimiteMinutos;
        this.activa = activa;
        this.creadaPor = creadaPor;
        this.creadaEn = creadaEn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCasoId() {
        return casoId;
    }

    public String getTitulo() {
        return titulo;
    }
}
