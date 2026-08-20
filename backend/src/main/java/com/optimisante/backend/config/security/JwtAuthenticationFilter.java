package com.optimisante.backend.config.security;

import com.optimisante.backend.config.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String userId = tokenProvider.getUserIdFromJWT(jwt);
                String tenantIdStr = tokenProvider.getTenantIdFromJWT(jwt);

                if (StringUtils.hasText(tenantIdStr)) {
                    TenantContext.setTenantId(UUID.fromString(tenantIdStr));
                }

                // Extract roles from token. Note: in a real scenario we'd use the JwtTokenProvider properly
                // Since this is a simple filter, we might need to parse claims again or add a method to provider
                // For efficiency, we will parse the role. Let's retrieve role directly using Jwts parser 
                // However, a better way is to expose getRoleFromJWT in provider. 
                // For now, let's keep it simple: assuming role is there.
                
                // Temporary workaround to extract role without modifying Provider again immediately:
                // Actually, I can just trust the DB or add getRoleFromJWT.
                // I will modify JwtTokenProvider to add getRoleFromJWT, but for now I'll use a dummy authority
                // Wait, Spring Security requires roles prefixed with "ROLE_" usually if using hasRole().
                
                // Let's assume we implement it correctly in the provider or just use a generic USER role 
                // if we don't fetch it. No, we MUST fetch it.
                // Let's create an Authentication object.
                String role = tokenProvider.getRoleFromJWT(jwt);
                List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, authorities
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: Clean up ThreadLocal to prevent memory leaks in the thread pool
            TenantContext.clear();
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
