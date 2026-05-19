package com.dipdv.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

/**
 * Configuração central do Spring Security.
 *
 * DECISÕES DE DESIGN:
 *
 * 1. STATELESS: Sem sessão HTTP — cada request é autenticado via JWT.
 *    SessionCreationPolicy.STATELESS garante que o Spring nunca crie HttpSession.
 *
 * 2. CSRF desabilitado: Não necessário para APIs REST stateless com JWT.
 *    CSRF protege contra ataques em formulários com sessão — não se aplica aqui.
 *
 * 3. Rotas públicas por módulo: Cada módulo define suas rotas abertas
 *    diretamente neste SecurityConfig. Mantém a configuração centralizada
 *    e auditável em um único lugar.
 *
 * 4. @PreAuthorize nos Controllers: Habilitado via @EnableMethodSecurity.
 *    Autorização por role granular sem duplicar lógica de segurança.
 *
 * 5. JwtAuthFilter antes do UsernamePasswordAuthenticationFilter:
 *    Garante que o token seja validado antes de qualquer tentativa
 *    de autenticação via username/password.
 *
 * ROLES DISPONÍVEIS:
 *   ROLE_ADMIN   → acesso total
 *   ROLE_MANAGER → relatórios, fechamento de caixa, estoque
 *   ROLE_CASHIER → apenas PDV e abertura de caixa
 *
 * USO NOS CONTROLLERS:
 *   @PreAuthorize("hasRole('ADMIN')")
 *   @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
 *   @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // Habilita @PreAuthorize nos Controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── Desabilitar o que não usamos ────────────────────────────────
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // ── Sessão stateless (JWT) ───────────────────────────────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── CORS ────────────────────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Rotas públicas e protegidas ─────────────────────────────────
            .authorizeHttpRequests(auth -> auth

                // ── Módulo Auth (público) ──────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/logout").authenticated()

                // ── Health check e documentação (público) ─────────────────
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()

                // ── Módulo Catalog ─────────────────────────────────────────
                // Leitura pública do cardápio (para futura integração WhatsApp/iFood)
                // Escrita protegida — definida via @PreAuthorize no Controller
                .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").authenticated()

                // ── Todos os demais endpoints exigem autenticação ──────────
                // A granularidade de role (ADMIN/MANAGER/CASHIER)
                // é definida por @PreAuthorize em cada Controller
                .anyRequest().authenticated()
            )

            // ── Tratamento de erros de autenticação ─────────────────────────
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("""
                        {"error":"UNAUTHORIZED","message":"Token ausente ou inválido","status":401}
                        """);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("""
                        {"error":"FORBIDDEN","message":"Sem permissão para esta operação","status":403}
                        """);
                })
            )

            // ── Inserir JwtAuthFilter antes do filtro padrão do Spring ───────
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt com fator 12 — equilíbrio entre segurança e performance.
     * Fator 10 = ~100ms por hash (padrão), fator 12 = ~400ms.
     * Para um PDV com poucos logins por dia, fator 12 é adequado.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * CORS: permite frontends configurados por env var acessar a API.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(allowedOrigins.split(","))
            .stream()
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .toList());

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);        // Cache do preflight por 1 hora

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/actuator/**", config);
        return source;
    }
}
