package com.dipdv.modules.admin.controller;

import com.dipdv.modules.admin.dto.AdminLoginRequest;
import com.dipdv.modules.admin.dto.AdminLoginResponse;
import com.dipdv.modules.admin.dto.GlobalStatsResponse;
import com.dipdv.modules.admin.dto.TenantMetricsResponse;
import com.dipdv.modules.admin.dto.TenantSummaryResponse;
import com.dipdv.modules.admin.service.AdminMetricsService;
import com.dipdv.modules.admin.service.AdminTenantService;
import com.dipdv.modules.auth.dto.AuthResponse;
import com.dipdv.modules.auth.dto.LoginRequest;
import com.dipdv.modules.auth.entity.enums.UserRole;
import com.dipdv.modules.auth.service.AuthService;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.security.MasterTenantConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Painel SUPER_ADMIN — gestão de tenants")
public class AdminController {

    private static final String ADMIN_TOKEN_COOKIE = "dipdv_admin_token";

    private final AdminTenantService adminTenantService;
    private final AdminMetricsService adminMetricsService;
    private final AuthService authService;
    private final Environment environment;

    @PostMapping("/auth/login")
    @Operation(summary = "Login do SUPER_ADMIN via cookie seguro")
    public ResponseEntity<AdminLoginResponse> adminLogin(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletResponse response) {

        AuthResponse auth = authService.login(new LoginRequest(
                MasterTenantConstants.MASTER_TENANT_ID,
                request.email(),
                request.password()
        ));

        if (auth.role() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(
                    "Acesso restrito ao painel administrativo",
                    HttpStatus.FORBIDDEN);
        }

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                buildAdminCookie(auth.token(), Duration.ofHours(8)).toString());

        return ResponseEntity.ok(new AdminLoginResponse(
                auth.userId(),
                auth.name(),
                auth.role().name()
        ));
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "Logout do SUPER_ADMIN e limpeza do cookie seguro")
    public ResponseEntity<Void> adminLogout(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                buildAdminCookie("", Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants/{id}/summary")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Visão 360º de um tenant específico")
    public ResponseEntity<TenantSummaryResponse> getTenantSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(adminTenantService.getTenantSummary(id));
    }

    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Métricas globais da plataforma")
    public ResponseEntity<GlobalStatsResponse> getGlobalStats() {
        return ResponseEntity.ok(adminMetricsService.getGlobalStats());
    }

    @GetMapping("/dashboard/engagement")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Health check de engajamento por tenant")
    public ResponseEntity<List<TenantMetricsResponse>> getEngagementMetrics() {
        return ResponseEntity.ok(adminMetricsService.getEngagementMetrics());
    }

    private ResponseCookie buildAdminCookie(String token, Duration maxAge) {
        return ResponseCookie.from(ADMIN_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(!environment.acceptsProfiles(Profiles.of("dev")))
                .path("/")
                .maxAge(maxAge)
                .sameSite("Strict")
                .build();
    }
}
