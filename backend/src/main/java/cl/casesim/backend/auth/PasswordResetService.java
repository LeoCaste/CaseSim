package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.ForgotPasswordRequest;
import cl.casesim.backend.auth.dto.ResetPasswordRequest;
import cl.casesim.backend.common.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_EXPIRATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetSettings passwordResetSettings;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetSettings passwordResetSettings
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetSettings = passwordResetSettings;
    }

    @Transactional
    public String requestReset(ForgotPasswordRequest request) {
        PasswordResetMode mode = passwordResetSettings.mode();
        if (mode == PasswordResetMode.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La recuperación de contraseña está deshabilitada.");
        }

        if (mode == PasswordResetMode.MANUAL) {
            return "Recuperación por correo no habilitada en este entorno. Contacta al administrador técnico.";
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<AppUser> maybeUser = userRepository.findByEmailIgnoreCaseAndActivoTrue(normalizedEmail)
                .filter(this::isAdmin);

        if (maybeUser.isEmpty()) {
            log.info("Password reset solicitado para email no elegible.");
            return "Si el email existe, recibirás instrucciones para restablecer tu contraseña.";
        }

        AppUser user = maybeUser.get();
        issueToken(user);
        return "Si el email existe, recibirás instrucciones para restablecer tu contraseña.";
    }

    @Transactional
    public String generateAdminResetUrl(String email, String operationsToken) {
        if (passwordResetSettings.mode() != PasswordResetMode.MANUAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El endpoint manual solo está disponible en modo MANUAL.");
        }

        validateOperationsToken(operationsToken);

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        AppUser user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));

        if (!user.isActivo() || !isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El usuario no es un administrador activo.");
        }

        String rawToken = issueToken(user);
        String resetUrl = passwordResetSettings.frontendBaseUrl() + "/reset-password?token=" + rawToken;
        log.info("event=ADMIN_RESET_TOKEN_GENERATED userId={} mode={} expiresInMinutes={}",
                user.getId(), passwordResetSettings.mode().name(), TOKEN_EXPIRATION_MINUTES);
        return resetUrl;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException("confirmPassword: Las contraseñas no coinciden.");
        }

        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken token = passwordResetTokenRepository
                .findValidByTokenHash(hashToken(request.token()), now)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token inválido o expirado."));

        AppUser user = token.getUser();
        user.actualizarPasswordHash(passwordEncoder.encode(request.password()));
        token.markUsed(now);
        passwordResetTokenRepository.invalidateActiveTokens(user.getId(), now);

        log.info("event=ADMIN_PASSWORD_RESET_COMPLETED userId={}", user.getId());
    }

    private String issueToken(AppUser user) {
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.invalidateActiveTokens(user.getId(), now);

        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        PasswordResetToken token = new PasswordResetToken(
                UUID.randomUUID(),
                user,
                hashToken(rawToken),
                now.plusMinutes(TOKEN_EXPIRATION_MINUTES),
                null,
                now
        );
        passwordResetTokenRepository.save(token);
        return rawToken;
    }

    private void validateOperationsToken(String providedToken) {
        String expectedToken = passwordResetSettings.resolvedOperationsToken();
        if (expectedToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token operativo no configurado.");
        }
        if (providedToken == null || providedToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token operativo requerido.");
        }
        if (!expectedToken.equals(providedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token operativo inválido.");
        }
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.", e);
        }
    }

    private boolean isAdmin(AppUser user) {
        return user.getRoles().stream().map(Role::getUserRole).anyMatch(role -> role == UserRole.ADMIN);
    }
}
