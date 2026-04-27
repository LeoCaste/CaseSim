package cl.casesim.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinical-cases/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sessions").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sessions/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sessions/*/messages").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sessions/*/final-diagnosis").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sessions/*/complete").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
