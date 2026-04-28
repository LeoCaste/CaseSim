package cl.casesim.backend.adminusers;

import cl.casesim.backend.adminusers.dto.AdminUserResponse;
import cl.casesim.backend.adminusers.dto.AdminUserRoleResponse;
import cl.casesim.backend.adminusers.dto.CreateAdminUserRequest;
import cl.casesim.backend.adminusers.dto.UpdateAdminUserRequest;
import cl.casesim.backend.adminusers.dto.UpdateAdminUserStatusRequest;
import cl.casesim.backend.auth.AppUser;
import cl.casesim.backend.auth.Role;
import cl.casesim.backend.auth.RoleRepository;
import cl.casesim.backend.auth.UserRepository;
import cl.casesim.backend.auth.UserRole;
import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.common.exception.ConflictException;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminUsersService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUsersService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AdminUserResponse> getUsers() {
        return userRepository.findAllByOrderByNombreAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AdminUserRoleResponse> getRoles() {
        return roleRepository.findAllByOrderByNombreAsc()
                .stream()
                .map(role -> new AdminUserRoleResponse(role.getNombre()))
                .toList();
    }

    public AdminUserResponse getUserById(UUID id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse createUser(CreateAdminUserRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Ya existe un usuario con ese email.");
        }

        Set<Role> roles = resolveRole(request.role());
        String passwordHash = resolvePasswordHashForCreate(roles, request.password());

        AppUser user = new AppUser(
                UUID.randomUUID(),
                request.name().trim(),
                normalizedEmail,
                passwordHash,
                true,
                roles
        );

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateUser(UUID id, UpdateAdminUserRequest request) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));

        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, id)) {
            throw new ConflictException("Ya existe un usuario con ese email.");
        }

        Set<Role> roles = resolveRole(request.role());

        user.actualizarDatos(
                request.name().trim(),
                normalizedEmail,
                user.isActivo(),
                roles
        );

        applyPasswordUpdateRules(user, roles, request.password());

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateUserStatus(UUID id, UpdateAdminUserStatusRequest request) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));

        user.actualizarDatos(
                user.getNombre(),
                user.getEmail(),
                request.active(),
                user.getRoles()
        );

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deactivateUser(UUID id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));

        user.desactivar();
        userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolvePasswordHashForCreate(Set<Role> roles, String password) {
        boolean adminRequested = hasAdminRole(roles);
        String normalizedPassword = normalizeOptionalPassword(password);

        if (adminRequested && normalizedPassword == null) {
            throw new BadRequestException("La contraseña es obligatoria para rol ADMIN.");
        }

        if (normalizedPassword == null) {
            return passwordEncoder.encode(UUID.randomUUID().toString());
        }

        return passwordEncoder.encode(normalizedPassword);
    }

    private void applyPasswordUpdateRules(AppUser user, Set<Role> targetRoles, String password) {
        boolean currentIsAdmin = hasAdminRole(user.getRoles());
        boolean targetIsAdmin = hasAdminRole(targetRoles);
        String normalizedPassword = normalizeOptionalPassword(password);

        if (targetIsAdmin && !currentIsAdmin && normalizedPassword == null) {
            throw new BadRequestException("La contraseña es obligatoria para rol ADMIN.");
        }

        if (normalizedPassword != null) {
            user.actualizarPasswordHash(passwordEncoder.encode(normalizedPassword));
            return;
        }

        if (targetIsAdmin && (user.getPasswordHash() == null || user.getPasswordHash().isBlank())) {
            throw new BadRequestException("La contraseña es obligatoria para rol ADMIN.");
        }
    }

    private boolean hasAdminRole(Set<Role> roles) {
        return roles.stream()
                .map(Role::getUserRole)
                .anyMatch(role -> role == UserRole.ADMIN);
    }

    private String normalizeOptionalPassword(String password) {
        if (password == null) {
            return null;
        }

        String normalizedPassword = password.trim();
        if (normalizedPassword.isEmpty()) {
            return null;
        }

        return normalizedPassword;
    }

    private Set<Role> resolveRole(String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            throw new BadRequestException("Debe asignar un rol.");
        }

        final String normalizedRole;
        try {
            normalizedRole = UserRole.fromDbValue(requestedRole.trim()).name();
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Rol inválido: " + requestedRole);
        }

        Role role = roleRepository.findByNombreIgnoreCase(normalizedRole)
                .orElseThrow(() -> new BadRequestException("Rol no encontrado: " + normalizedRole));

        return Set.of(role);
    }

    private AdminUserResponse toResponse(AppUser user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getNombre)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new AdminUserResponse(
                user.getId(),
                user.getNombre(),
                user.getEmail(),
                user.isActivo(),
                roles
        );
    }
}
