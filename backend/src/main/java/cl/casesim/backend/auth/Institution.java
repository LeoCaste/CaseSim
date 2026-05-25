package cl.casesim.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "institucion")
public class Institution {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "tipo", length = 100)
    private String tipo;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    protected Institution() {
    }

    public Institution(UUID id, String nombre, String tipo, boolean activo, LocalDateTime creadoEn) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.activo = activo;
        this.creadoEn = creadoEn;
    }

    public void actualizarNombre(String nombre) {
        this.nombre = nombre;
    }
}
