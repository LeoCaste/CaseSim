package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.AuthUserResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthMeSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private AuthService authService;

    @Test
    void meWithoutTokenShouldReturnSemanticUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("No autenticado."));
    }

    @Test
    void meWithInvalidTokenShouldReturnSemanticInvalidToken() throws Exception {
        when(jwtService.extractUsername("invalid-token")).thenThrow(new JwtException("invalid"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message").value("Token inválido."));
    }

    @Test
    void meWithExpiredTokenShouldReturnSemanticExpiredToken() throws Exception {
        Claims claims = Jwts.claims().build();
        when(jwtService.extractUsername("expired-token")).thenThrow(new ExpiredJwtException(null, claims, "expired"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").value("Token expirado."));
    }

    @Test
    void meWithValidTokenShouldReturnUser() throws Exception {
        String email = "profesor@casesim.cl";
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(),
                "Profesor Demo",
                email,
                "hash",
                true,
                Set.of(UserRole.PROFESOR)
        );

        when(jwtService.extractUsername("valid-token")).thenReturn(email);
        when(jwtService.extractRoles("valid-token")).thenReturn(java.util.List.of("PROFESOR"));
        when(userDetailsService.loadUserByUsername(email)).thenReturn(principal);
        when(jwtService.isValidToken("valid-token", principal)).thenReturn(true);
        when(authService.me(any(UserPrincipal.class))).thenReturn(new AuthUserResponse(
                principal.getId(),
                principal.getName(),
                principal.getUsername(),
                Set.of("PROFESOR"),
                true
        ));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.roles[0]").value("PROFESOR"));
    }
}
