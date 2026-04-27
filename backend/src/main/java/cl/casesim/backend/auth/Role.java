package cl.casesim.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "rol")
public class Role {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, unique = true, length = 50)
    private String nombre;

    protected Role() {
        // Constructor requerido por JPA
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public UserRole getUserRole() {
        return UserRole.fromDbValue(nombre);
    }
}
