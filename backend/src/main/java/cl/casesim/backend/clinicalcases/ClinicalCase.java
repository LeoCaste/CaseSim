package cl.casesim.backend.clinicalcases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "caso_clinico")
public class ClinicalCase {

    private static final String DEFAULT_NO_INFORMATION_PHRASE = "No tengo información asociada a eso.";

    protected ClinicalCase() {
        // Constructor requerido por JPA
    }

    public ClinicalCase(
            UUID id,
            String titulo,
            String descripcion,
            String pacienteNombre,
            Integer pacienteEdad,
            String pacienteSexo,
            String motivoConsulta,
            String fraseSinInformacion,
             boolean activo,
             UUID creadoPor,
             LocalDateTime creadoEn
    ) {
        this(id, titulo, descripcion, pacienteNombre, pacienteEdad, pacienteSexo, motivoConsulta,
                fraseSinInformacion, activo, null, creadoPor, creadoEn);
    }

    public ClinicalCase(
            UUID id,
            String titulo,
            String descripcion,
            String pacienteNombre,
            Integer pacienteEdad,
            String pacienteSexo,
            String motivoConsulta,
            String fraseSinInformacion,
            boolean activo,
            ClinicalCaseStatus status,
            UUID creadoPor,
            LocalDateTime creadoEn
    ) {
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.pacienteNombre = pacienteNombre;
        this.pacienteEdad = pacienteEdad;
        this.pacienteSexo = pacienteSexo;
        this.motivoConsulta = motivoConsulta;
        this.fraseSinInformacion = fraseSinInformacion;
        this.activo = activo;
        this.status = status;
        this.creadoPor = creadoPor;
        this.creadoEn = creadoEn;
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "paciente_nombre", length = 120)
    private String pacienteNombre;

    @Column(name = "paciente_edad")
    private Integer pacienteEdad;

    @Column(name = "paciente_sexo", length = 30)
    private String pacienteSexo;

    @Column(name = "motivo_consulta", nullable = false)
    private String motivoConsulta;

    @Column(name = "frase_sin_informacion", nullable = false)
    private String fraseSinInformacion;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClinicalCaseStatus status;

    @Column(name = "creado_por")
    private UUID creadoPor;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    public UUID getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getPacienteNombre() {
        return pacienteNombre;
    }

    public Integer getPacienteEdad() {
        return pacienteEdad;
    }

    public String getPacienteSexo() {
        return pacienteSexo;
    }

    public String getMotivoConsulta() {
        return motivoConsulta;
    }

    public String getFraseSinInformacion() {
        return fraseSinInformacion;
    }

    public boolean isActivo() {
        return activo;
    }

    public ClinicalCaseStatus getStatus() {
        if (status == null) {
            return ClinicalCaseStatus.fromLegacyActive(activo);
        }
        return status;
    }

    public UUID getCreadoPor() {
        return creadoPor;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void actualizarDatos(
            String titulo,
            String descripcion,
            String pacienteNombre,
            Integer pacienteEdad,
            String pacienteSexo,
            String motivoConsulta,
            String fraseSinInformacion,
            boolean activo,
            ClinicalCaseStatus status
    ) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.pacienteNombre = pacienteNombre;
        this.pacienteEdad = pacienteEdad;
        this.pacienteSexo = pacienteSexo;
        this.motivoConsulta = motivoConsulta;
        this.fraseSinInformacion = fraseSinInformacion;
        this.activo = activo;
        this.status = status;
    }

    public void desactivar() {
        this.activo = false;
        this.status = ClinicalCaseStatus.ARCHIVED;
        if (this.fraseSinInformacion == null || this.fraseSinInformacion.trim().isEmpty()) {
            this.fraseSinInformacion = DEFAULT_NO_INFORMATION_PHRASE;
        }
    }
}
