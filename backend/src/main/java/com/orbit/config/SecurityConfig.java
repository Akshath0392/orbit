package com.orbit.config;

import com.orbit.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final Environment environment;

    public SecurityConfig(JwtFilter jwtFilter, Environment environment) {
        this.jwtFilter = jwtFilter;
        this.environment = environment;
    }

    private boolean isProd() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean prod = isProd();
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/auth/**", "/ws/**", "/api/jira/**",
                        "/api/v1/hrms/webhook", "/api/v1/slack/**").permitAll();
                // Swagger / API docs are public only outside prod (L2).
                if (!prod) {
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                        "/api-docs/**", "/v3/api-docs/**").permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> res.sendError(401, "Unauthorized"))
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    CorsConfigurationSource corsSource() {
        String raw = System.getenv("ORBIT_CORS_ALLOWED_ORIGINS");
        List<String> origins;
        if (raw != null && !raw.isBlank()) {
            origins = List.of(raw.split(","));
        } else if (isProd()) {
            // Fail closed in prod — do not silently allow localhost when the env var
            // is missing (M4). With credentials enabled this must be an explicit list.
            origins = List.of();
        } else {
            origins = List.of("http://localhost:3000", "http://localhost:5173");
        }
        var cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        // Scope allowed headers instead of a wildcard (M4).
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        cfg.setAllowCredentials(true);
        var src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
