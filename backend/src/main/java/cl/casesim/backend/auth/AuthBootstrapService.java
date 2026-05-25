package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.BootstrapAdminRequest;
import cl.casesim.backend.common.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AuthBootstrapService.class);

    private final PlatformSetupStateRepository setupStateRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InstitutionRepository institutionRepository;
    private final PasswordEncoder passwordEncoder;
    private final String configuredBootstrapToken;

    public AuthBootstrapService(
            PlatformSetupStateRepository setupStateRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            InstitutionRepository institutionRepository,
            PasswordEncoder passwordEncoder,
            @Value("${casesim.auth.bootstrap-token:}") String configuredBootstrapToken
    ) {
        this.setupStateRepository = setupStateRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.institutionRepository = institutionRepository;
        this.passwordEncoder = passwordEncoder;
        this.configuredBootstrapToken = configuredBootstrapToken;
    }

    @Transactional
    public boolean needsInitialSetup() {
        PlatformSetupState state = setupStateRepository.findById(PlatformSetupState.SINGLETON_ID).orElse(null);
        if (state != null && state.isInitialized()) {
            return false;
        }

        if (existsActiveAdmin()) {
            markInitializedIfRequired(state, LocalDateTime.now());
            return false;
        }

        return true;
    }

    @Transactional
    public void bootstrapAdmin(BootstrapAdminRequest request, String providedBootstrapToken) {
        if (!needsInitialSetup()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La plataforma ya fue inicializada.");
        }

        validateBootstrapToken(providedBootstrapToken);

        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException("confirmPassword: Las contraseñas no coinciden.");
        }

        String normalizedEmail = request.adminEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email del administrador ya existe.");
        }

        Role adminRole = roleRepository.findByNombreIgnoreCase(UserRole.ADMIN.name())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Rol ADMIN no configurado."));

        LocalDateTime now = LocalDateTime.now();
        AppUser admin = new AppUser(
                UUID.randomUUID(),
                request.adminName().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                true,
                Set.of(adminRole)
        );
        userRepository.save(admin);
        upsertInstitution(request.institutionName().trim(), now);
        markInitializedIfRequired(setupStateRepository.findById(PlatformSetupState.SINGLETON_ID).orElse(null), now);
        log.info("Bootstrap inicial completado. adminId={}", admin.getId());
    }

    private boolean existsActiveAdmin() {
        return userRepository.existsActiveAdmin();
    }

    private void validateBootstrapToken(String providedToken) {
        if (configuredBootstrapToken == null || configuredBootstrapToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "BOOTSTRAP_TOKEN no configurado.");
        }
        if (providedToken == null || providedToken.isBlank() || !configuredBootstrapToken.equals(providedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bootstrap token inválido.");
        }
    }

    private void markInitializedIfRequired(PlatformSetupState state, LocalDateTime now) {
        PlatformSetupState target = state;
        if (target == null) {
            target = new PlatformSetupState(PlatformSetupState.SINGLETON_ID, false, null, now, now);
        }
        target.markInitialized(now);
        setupStateRepository.save(target);
    }

    private void upsertInstitution(String institutionName, LocalDateTime now) {
        institutionRepository.findFirstByOrderByCreadoEnAsc()
                .ifPresentOrElse(
                        institution -> institution.actualizarNombre(institutionName),
                        () -> institutionRepository.save(new Institution(UUID.randomUUID(), institutionName, "universidad", true, now))
                );
    }
}
