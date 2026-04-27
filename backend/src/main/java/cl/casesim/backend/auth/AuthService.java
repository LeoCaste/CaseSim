package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.AuthUserResponse;
import cl.casesim.backend.auth.dto.LoginRequest;
import cl.casesim.backend.auth.dto.LoginResponse;
import org.springframework.http.HttpStatus;
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

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
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

        UserPrincipal principal = UserPrincipal.fromEntity(user);
        String token = jwtService.generateToken(principal);

        return new LoginResponse(token, toUserResponse(principal));
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me(UserPrincipal principal) {
        return toUserResponse(principal);
    }

    public void logout(UserPrincipal principal) {
        // MVP stateless: no se persiste revocación de token.
    }

    private ResponseStatusException unauthorizedException() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
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
