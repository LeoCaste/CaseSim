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

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<AppUser> maybeUser = userRepository.findByEmailIgnoreCaseAndActivoTrue(normalizedEmail)
                .filter(this::isAdmin);

        if (maybeUser.isEmpty()) {
            log.info("Password reset solicitado para email no elegible.");
            return;
        }

        AppUser user = maybeUser.get();
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.invalidateActiveTokens(user.getId(), now);

        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(
                UUID.randomUUID(),
                user,
                tokenHash,
                now.plusMinutes(TOKEN_EXPIRATION_MINUTES),
                null,
                now
        );
        passwordResetTokenRepository.save(token);
        log.warn("[STAGING_RESET_TOKEN] userId={} token={} expiresInMinutes={}", user.getId(), rawToken, TOKEN_EXPIRATION_MINUTES);
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

        log.info("Password reset completado para userId={}", user.getId());
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
