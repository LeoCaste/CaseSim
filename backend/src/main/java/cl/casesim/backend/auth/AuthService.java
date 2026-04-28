package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.AuthUserResponse;
import cl.casesim.backend.auth.dto.LoginRequest;
import cl.casesim.backend.auth.dto.LoginResponse;
import cl.casesim.backend.auth.dto.PreCheckRequest;
import cl.casesim.backend.auth.dto.PreCheckResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern INSTITUTIONAL_EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@(ufromail\\.cl|ufrontera\\.cl)$", Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (!INSTITUTIONAL_EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw unauthorizedException();
        }

        AppUser user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(this::unauthorizedException);

        if (!user.isActivo() || user.getRoles().isEmpty()) {
            throw unauthorizedException();
        }

        if (isAdmin(user)) {
            if (request.password() == null || request.password().isBlank()) {
                throw unauthorizedException();
            }

            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw unauthorizedException();
            }
        }

        UserPrincipal principal = UserPrincipal.fromEntity(user);
        String token = jwtService.generateToken(principal);

        return new LoginResponse(token, toUserResponse(principal));
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me(UserPrincipal principal) {
        return toUserResponse(principal);
    }

    @Transactional(readOnly = true)
    public PreCheckResponse preCheck(PreCheckRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (!INSTITUTIONAL_EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            return new PreCheckResponse(false);
        }

        boolean requiresPassword = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(AppUser::isActivo)
                .map(this::isAdmin)
                .orElse(false);

        return new PreCheckResponse(requiresPassword);
    }

    public void logout(UserPrincipal principal) {
        // MVP stateless: no se persiste revocación de token.
    }

    private ResponseStatusException unauthorizedException() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
    }

    private boolean isAdmin(AppUser user) {
        return user.getRoles().stream()
                .map(Role::getUserRole)
                .anyMatch(role -> role == UserRole.ADMIN);
    }

    private AuthUserResponse toUserResponse(UserPrincipal principal) {
        return new AuthUserResponse(
                principal.getId(),
                principal.getName(),
                principal.getUsername(),
                principal.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet())
        );
    }
}
