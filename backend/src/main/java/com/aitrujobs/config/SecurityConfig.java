package com.aitrujobs.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private com.aitrujobs.service.CustomUserDetailsService customUserDetailsService;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Stronger encryption
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        // Explicitly wire the DAO provider so programmatic authenticate() uses it
        return new ProviderManager(authenticationProvider());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/", "/index.html").permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                // Swagger / OpenAPI
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**"
                ).permitAll()
                // Recruiter-specific listing & management (must come BEFORE generic /api/jobs/* permitAll)
                .requestMatchers(HttpMethod.GET, "/api/jobs/recruiter", "/api/jobs/recruiter/*").hasAnyRole("RECRUITER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/jobs").hasAnyRole("RECRUITER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/jobs/*").hasAnyRole("RECRUITER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/jobs/*").hasAnyRole("RECRUITER", "ADMIN")
                // Public job browsing (list, detail, search). Placed AFTER recruiter protections so recruiter paths are not exposed.
                .requestMatchers(HttpMethod.GET, "/api/jobs", "/api/jobs/", "/api/jobs/search").permitAll()
                // Allow individual job detail (numeric id) publicly. Using wildcard still, but recruiter paths already matched above.
                .requestMatchers(HttpMethod.GET, "/api/jobs/*").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Protected endpoints
                .requestMatchers("/api/jobs/create").hasRole("RECRUITER")
                .requestMatchers("/api/jobs/*/edit").hasRole("RECRUITER")
                .requestMatchers("/api/applications/recruiter/**").hasRole("RECRUITER")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()) // Allow H2 console in same origin
            )
            .build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }
}
