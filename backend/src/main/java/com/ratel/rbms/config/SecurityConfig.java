package com.ratel.rbms.config;

import com.ratel.rbms.tenant.JwtAuthenticationFilter;
import com.ratel.rbms.tenant.ReadOnlyEnforcementFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ReadOnlyEnforcementFilter readOnlyEnforcementFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ReadOnlyEnforcementFilter readOnlyEnforcementFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.readOnlyEnforcementFilter = readOnlyEnforcementFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless JWT API, no cookies to protect
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/google/register",
                                "/api/auth/google/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        ).permitAll()
                        .requestMatchers("/api/platform/auth/login").permitAll()
                        .requestMatchers("/api/platform/auth/forgot-password").permitAll()
                        .requestMatchers("/api/platform/auth/reset-password").permitAll()
                        .requestMatchers("/api/platform/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/webhooks/paystack").permitAll()
                        .requestMatchers("/api/webhooks/woocommerce/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/widget/**").permitAll()
                        .requestMatchers("/branding/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(readOnlyEnforcementFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        // The booking/wig-configurator widgets embed on arbitrary client
        // WordPress sites we can't enumerate in advance, so /api/public/**
        // needs a permissive CORS policy of its own — unlike every other
        // endpoint, which only ever gets called from Ratel's own frontend.
        // No credentials (cookies) are involved on this path, only whatever
        // businessId/manage_token the widget itself carries, so wildcard
        // origins here don't widen who can act as an authenticated user.
        CorsConfiguration publicConfiguration = new CorsConfiguration();
        publicConfiguration.setAllowedOriginPatterns(List.of("*"));
        publicConfiguration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        publicConfiguration.setAllowedHeaders(List.of("*"));
        publicConfiguration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/public/**", publicConfiguration);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
