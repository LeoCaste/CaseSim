package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.AuthUserResponse;
import cl.casesim.backend.auth.dto.AdminResetTokenRequest;
import cl.casesim.backend.auth.dto.AdminResetTokenResponse;
import cl.casesim.backend.auth.dto.BootstrapAdminRequest;
import cl.casesim.backend.auth.dto.BootstrapStatusResponse;
import cl.casesim.backend.auth.dto.ForgotPasswordRequest;
import cl.casesim.backend.auth.dto.ForgotPasswordResponse;
import cl.casesim.backend.auth.dto.LoginRequest;
import cl.casesim.backend.auth.dto.LoginResponse;
import cl.casesim.backend.auth.dto.MeResponse;
import cl.casesim.backend.auth.dto.PreCheckRequest;
import cl.casesim.backend.auth.dto.PreCheckResponse;
import cl.casesim.backend.auth.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthBootstrapService authBootstrapService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, AuthBootstrapService authBootstrapService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.authBootstrapService = authBootstrapService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/pre-check")
    public PreCheckResponse preCheck(@RequestBody(required = false) PreCheckRequest request) {
        return authService.preCheck(request);
    }

    @GetMapping("/bootstrap-status")
    public BootstrapStatusResponse bootstrapStatus() {
        return new BootstrapStatusResponse(authBootstrapService.needsInitialSetup());
    }

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<Void> bootstrapAdmin(
            @RequestHeader(name = "X-Bootstrap-Token", required = false) String bootstrapToken,
            @Valid @RequestBody BootstrapAdminRequest request
    ) {
        authBootstrapService.bootstrapAdmin(request, bootstrapToken);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String message = passwordResetService.requestReset(request);
        return new ForgotPasswordResponse(message);
    }

    @PostMapping("/admin-reset-token")
    public AdminResetTokenResponse adminResetToken(
            @RequestHeader(name = "X-Setup-Token", required = false) String operationsToken,
            @Valid @RequestBody AdminResetTokenRequest request
    ) {
        String resetUrl = passwordResetService.generateAdminResetUrl(request.email(), operationsToken);
        return new AdminResetTokenResponse(resetUrl);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado.");
        }

        AuthUserResponse user = authService.me(principal);
        return new MeResponse(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
