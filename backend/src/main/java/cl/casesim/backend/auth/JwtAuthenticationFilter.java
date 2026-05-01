package cl.casesim.backend.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_FAILURE_REASON_ATTR = "casesim.auth.failureReason";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "bearer ";
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        String method = request.getMethod();
        String endpoint = request.getRequestURI();

        if (authHeader == null) {
            request.setAttribute(AUTH_FAILURE_REASON_ATTR, "token ausente");
            log.debug("JWT sin header Authorization. method={} endpoint={}", method, endpoint);
            filterChain.doFilter(request, response);
            return;
        }

        String normalizedHeader = authHeader.trim();
        if (normalizedHeader.length() <= BEARER_PREFIX.length()
                || !normalizedHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            request.setAttribute(AUTH_FAILURE_REASON_ATTR, "esquema Authorization inválido");
            log.debug("JWT omitido por esquema inválido. method={} endpoint={}", method, endpoint);
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = normalizedHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            String username = jwtService.extractUsername(jwt);
            List<String> jwtRoles = jwtService.extractRoles(jwt);
            if (jwtRoles == null) {
                jwtRoles = Collections.emptyList();
            }

            String normalizedUsername = normalizeUsername(username);
            log.debug("JWT parseado. method={} endpoint={} subExtraido='{}' subNormalizado='{}' rolesClaim={}",
                    method, endpoint, username, normalizedUsername, jwtRoles);

            if (normalizedUsername != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(normalizedUsername);
                log.debug("JWT userDetails cargado. method={} endpoint={} username={} enabled={} authorities={}",
                        method, endpoint, userDetails.getUsername(), userDetails.isEnabled(), userDetails.getAuthorities());

                boolean tokenValid = jwtService.isValidToken(jwt, userDetails);
                log.debug("JWT validación. method={} endpoint={} username={} isValidToken={}",
                        method, endpoint, normalizedUsername, tokenValid);

                if (tokenValid) {
                    Collection<? extends GrantedAuthority> authorities = jwtRoles.stream()
                            .map(this::normalizeRole)
                            .filter(role -> !role.isBlank())
                            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                            .map(role -> role.toUpperCase(Locale.ROOT))
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    if (authorities.isEmpty()) {
                        authorities = userDetails.getAuthorities().stream().toList();
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            authorities
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("JWT autenticado. method={} endpoint={} username={} jwtRoles={} authorities={}",
                            method,
                            endpoint,
                            username,
                            jwtRoles,
                            authToken.getAuthorities());
                } else {
                    request.setAttribute(AUTH_FAILURE_REASON_ATTR, "token inválido para usuario");
                    log.warn("JWT inválido para usuario. method={} endpoint={} username={}", method, endpoint, username);
                }
            }
        } catch (JwtException ex) {
            request.setAttribute(AUTH_FAILURE_REASON_ATTR, "token inválido o expirado");
            log.warn("JWT rechazado. method={} endpoint={} motivo={}", method, endpoint, ex.getMessage());
            SecurityContextHolder.clearContext();
        } catch (IllegalArgumentException ex) {
            request.setAttribute(AUTH_FAILURE_REASON_ATTR, "token inválido");
            log.warn("JWT inválido. method={} endpoint={} motivo={}", method, endpoint, ex.getMessage());
            SecurityContextHolder.clearContext();
        } catch (AuthenticationException ex) {
            request.setAttribute(AUTH_FAILURE_REASON_ATTR, "usuario inexistente o inactivo");
            log.warn("Usuario JWT no autenticable. method={} endpoint={} motivo={}", method, endpoint, ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            log.debug("Request sin Authentication en contexto. method={} endpoint={} motivo={}",
                    method,
                    endpoint,
                    request.getAttribute(AUTH_FAILURE_REASON_ATTR));
        }

        filterChain.doFilter(request, response);
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim();
    }
}
