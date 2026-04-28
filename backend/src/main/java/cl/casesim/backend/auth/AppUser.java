package cl.casesim.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class AppUser {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id")
    )
    private Set<Role> roles = new HashSet<>();

    protected AppUser() {
        // Constructor requerido por JPA
    }

    public AppUser(UUID id, String nombre, String email, String passwordHash, boolean activo, Set<Role> roles) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.nombre = Objects.requireNonNull(nombre, "nombre es obligatorio");
        this.email = Objects.requireNonNull(email, "email es obligatorio");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash es obligatorio");
        this.activo = activo;
        this.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActivo() {
        return activo;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void actualizarDatos(String nombre, String email, boolean activo, Set<Role> roles) {
        this.nombre = Objects.requireNonNull(nombre, "nombre es obligatorio");
        this.email = Objects.requireNonNull(email, "email es obligatorio");
        this.activo = activo;
        Set<Role> roleCopy = roles == null ? Set.of() : new HashSet<>(roles);
        this.roles.clear();
        this.roles.addAll(roleCopy);
    }

    public void actualizarPasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash es obligatorio");
    }

    public void desactivar() {
        this.activo = false;
    }

    public void actualizarEstado(boolean activo) {
        this.activo = activo;
    }
}
