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

import com.optimisante.backend.domain.identity.repository.TenantRepository;
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
    private final TenantRepository tenantRepository;

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
                
                String role = tokenProvider.getRoleFromJWT(jwt);
                List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, authorities
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            
            // If TenantContext is still empty (e.g. anonymous user), try to get it from header
            if (TenantContext.getTenantId() == null) {
                String headerTenant = request.getHeader("X-Tenant-Id");
                if (StringUtils.hasText(headerTenant)) {
                    try {
                        TenantContext.setTenantId(UUID.fromString(headerTenant));
                    } catch (IllegalArgumentException e) {
                        // It might be a code instead of UUID, let's look it up
                        tenantRepository.findByCode(headerTenant).ifPresent(tenant -> 
                            TenantContext.setTenantId(tenant.getId())
                        );
                    }
                }
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
