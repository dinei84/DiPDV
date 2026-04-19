package com.dipdv.shared.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private Claims claims;

    @Test
    void shouldAuthenticateUsingAdminCookieWhenAuthorizationHeaderIsAbsent()
            throws ServletException, IOException {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("dipdv_admin_token", "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        mockJwtValidation("cookie-token");

        filter.doFilterInternal(request, response, new MockFilterChain());

        verify(jwtService).validateAndExtractClaims("cookie-token");
    }

    @Test
    void shouldPreferAuthorizationHeaderOverAdminCookie()
            throws ServletException, IOException {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token");
        request.setCookies(new Cookie("dipdv_admin_token", "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        mockJwtValidation("header-token");

        filter.doFilterInternal(request, response, new MockFilterChain());

        verify(jwtService).validateAndExtractClaims("header-token");
        verify(jwtService, never()).validateAndExtractClaims("cookie-token");
    }

    private void mockJwtValidation(String token) {
        when(jwtService.validateAndExtractClaims(token)).thenReturn(claims);
        when(jwtService.extractUserId(claims)).thenReturn(UUID.randomUUID());
        when(jwtService.extractTenantId(claims)).thenReturn(UUID.randomUUID());
        when(jwtService.extractRole(claims)).thenReturn("SUPER_ADMIN");
    }
}
