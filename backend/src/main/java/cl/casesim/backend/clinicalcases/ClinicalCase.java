package cl.casesim.backend.clinicalcases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "caso_clinico")
public class ClinicalCase {

    protected ClinicalCase() {
        // Constructor requerido por JPA
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

    public UUID getCreadoPor() {
        return creadoPor;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
