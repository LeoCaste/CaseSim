package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.BootstrapAdminRequest;
import cl.casesim.backend.common.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AuthBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AuthBootstrapService.class);
    private static final String INITIAL_ADMIN_NAME = "Administrador";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PlatformSetupStateRepository platformSetupStateRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReentrantLock bootstrapLock = new ReentrantLock();

    public AuthBootstrapService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PlatformSetupStateRepository platformSetupStateRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.platformSetupStateRepository = platformSetupStateRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public boolean needsInitialSetup() {
        return !adminExists();
    }

    @Transactional
    public void bootstrapAdmin(BootstrapAdminRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException("confirmPassword: Las contraseñas no coinciden.");
        }

        bootstrapLock.lock();
        try {
            PlatformSetupState setupState = platformSetupStateRepository.findByIdForUpdate(PlatformSetupState.SINGLETON_ID)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Estado de bootstrap no configurado."));

            if (setupState.isInitialized()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un administrador. El bootstrap inicial está bloqueado.");
            }

            if (adminExists()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un administrador. El bootstrap inicial está bloqueado.");
            }

            String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
            if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El email del administrador ya existe.");
            }

            Role adminRole = roleRepository.findByNombreIgnoreCase(UserRole.ADMIN.name())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Rol ADMIN no configurado."));

            AppUser admin = new AppUser(
                    UUID.randomUUID(),
                    INITIAL_ADMIN_NAME,
                    normalizedEmail,
                    passwordEncoder.encode(request.password()),
                    true,
                    Set.of(adminRole)
            );
            userRepository.save(admin);
            setupState.markInitialized(java.time.LocalDateTime.now());
            platformSetupStateRepository.save(setupState);
            log.info("Bootstrap inicial completado. adminId={}", admin.getId());
        } finally {
            bootstrapLock.unlock();
        }
    }

    @Transactional(readOnly = true)
    public boolean adminExists() {
        return userRepository.existsAdmin();
    }
}
