package cl.casesim.backend.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final boolean active;
    private final Set<UserRole> roles;

    public UserPrincipal(
            UUID id,
            String name,
            String email,
            String passwordHash,
            boolean active,
            Set<UserRole> roles
    ) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.active = active;
        this.roles = roles;
    }

    public static UserPrincipal fromEntity(AppUser user) {
        Set<UserRole> roleSet = user.getRoles().stream()
                .map(Role::getUserRole)
                .collect(java.util.stream.Collectors.toSet());

        return new UserPrincipal(
                user.getId(),
                user.getNombre(),
                user.getEmail(),
                user.getPasswordHash(),
                user.isActivo(),
                roleSet
        );
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<UserRole> getRoles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
