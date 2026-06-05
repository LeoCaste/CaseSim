package cl.casesim.backend.config;

import cl.casesim.backend.auth.JwtAuthenticationFilter;
import cl.casesim.backend.common.exception.ErrorCode;
import cl.casesim.backend.auth.JwtProperties;
import cl.casesim.backend.common.exception.ErrorResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final String corsAllowedOrigins;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${app.security.cors.allowed-origins:http://localhost:4200}") String corsAllowedOrigins,
            ObjectMapper objectMapper
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/pre-check").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/bootstrap-status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/bootstrap-admin").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/admin-reset-token").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers("/api/v1/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/llm/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/professor/**").hasRole("PROFESOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/simulations").hasRole("PROFESOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/student/activities").hasRole("ESTUDIANTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/student/clinical-cases/**").hasRole("ESTUDIANTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinical-cases/**").hasAnyRole("PROFESOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinical-cases/**").hasAnyRole("PROFESOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/clinical-cases/**").hasAnyRole("PROFESOR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/clinical-cases/**").hasAnyRole("PROFESOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/clinical-cases/**").hasAnyRole("PROFESOR", "ADMIN")
                        .requestMatchers("/api/v1/sessions/**").hasAnyRole("ESTUDIANTE", "PROFESOR", "ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_REASON_ATTR);
                            Object jwtEvent = request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_EVENT_ATTR);
                            String requestId = resolveRequestId(request);
                            ErrorCode code = resolveUnauthorizedCode(jwtEvent);
                            String message = resolveUnauthorizedMessage(code);
                            log.warn("{} method={} endpoint={} reason={} jwtEvent={} requestId={}",
                                    code.name(),
                                    request.getMethod(),
                                    request.getRequestURI(),
                                    reason == null ? "No autenticado." : reason,
                                    jwtEvent == null ? "N/A" : jwtEvent,
                                    requestId);
                            writeSecurityError(
                                    response,
                                    HttpStatus.UNAUTHORIZED,
                                    code,
                                    message,
                                    reason == null ? List.of() : List.of(new ErrorResponse.ErrorDetail("auth", reason.toString()))
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            String requestId = resolveRequestId(request);
                            log.warn("{} method={} endpoint={} principal={} reason={} requestId={}",
                                    ErrorCode.AUTH_FORBIDDEN.name(),
                                    request.getMethod(),
                                    request.getRequestURI(),
                                    request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName(),
                                    accessDeniedException.getMessage(),
                                    requestId);
                            writeSecurityError(
                                    response,
                                    HttpStatus.FORBIDDEN,
                                    ErrorCode.AUTH_FORBIDDEN,
                                    "Acceso denegado.",
                                    List.of(new ErrorResponse.ErrorDetail("auth", "Rol insuficiente para acceder al recurso."))
                            );
                        })
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(resolveAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> resolveAllowedOrigins() {
        return Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    private void writeSecurityError(
            HttpServletResponse response,
            HttpStatus status,
            ErrorCode code,
            String message,
            List<ErrorResponse.ErrorDetail> details
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ErrorResponse errorResponse = new ErrorResponse(status.value(), code.name(), message, details);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    private ErrorCode resolveUnauthorizedCode(Object jwtEvent) {
        if (jwtEvent == null) {
            return ErrorCode.AUTH_UNAUTHORIZED;
        }
        return switch (jwtEvent.toString()) {
            case "JWT_EXPIRED" -> ErrorCode.AUTH_TOKEN_EXPIRED;
            case "JWT_INVALID" -> ErrorCode.AUTH_TOKEN_INVALID;
            default -> ErrorCode.AUTH_UNAUTHORIZED;
        };
    }

    private String resolveUnauthorizedMessage(ErrorCode code) {
        return switch (code) {
            case AUTH_TOKEN_EXPIRED -> "Token expirado.";
            case AUTH_TOKEN_INVALID -> "Token inválido.";
            default -> "No autenticado.";
        };
    }

    private String resolveRequestId(jakarta.servlet.http.HttpServletRequest request) {
        Object attrRequestId = request.getAttribute("requestId");
        if (attrRequestId != null) {
            return attrRequestId.toString();
        }
        String headerRequestId = request.getHeader("X-Request-Id");
        if (headerRequestId != null && !headerRequestId.isBlank()) {
            return headerRequestId;
        }
        String correlationId = request.getHeader("X-Correlation-Id");
        return correlationId == null || correlationId.isBlank() ? "N/A" : correlationId;
    }
}
