package cl.casesim.backend.sessions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mensaje_chat")
public class ChatMessage {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "sesion_id", nullable = false)
    private UUID sesionId;

    @Column(name = "rol", nullable = false, length = 30)
    private String rol;

    @Column(name = "contenido", nullable = false)
    private String contenido;

    @Column(name = "numero_turno", nullable = false)
    private Integer numeroTurno;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    protected ChatMessage() {
        // Constructor requerido por JPA
    }

    public ChatMessage(
            UUID id,
            UUID sesionId,
            String rol,
            String contenido,
            Integer numeroTurno,
            LocalDateTime creadoEn
    ) {
        this.id = id;
        this.sesionId = sesionId;
        this.rol = rol;
        this.contenido = contenido;
        this.numeroTurno = numeroTurno;
        this.creadoEn = creadoEn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSesionId() {
        return sesionId;
    }

    public String getRol() {
        return rol;
    }

    public String getContenido() {
        return contenido;
    }

    public Integer getNumeroTurno() {
        return numeroTurno;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
