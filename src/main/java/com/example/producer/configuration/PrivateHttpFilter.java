package com.example.producer.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class PrivateHttpFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final byte[] expected;

    public PrivateHttpFilter(@Value("${app.private-http.enabled:false}") boolean enabled,
                             @Value("${app.private-http.deployment:false}") boolean deployment,
                             @Value("${app.private-http.user:}") String user,
                             @Value("${app.private-http.password:}") String password) {
        if (deployment && !enabled) {
            throw new IllegalStateException("Deployment requires private HTTP authentication");
        }
        if (enabled && (user.isBlank() || password.length() < 20 || user.contains(":"))) {
            throw new IllegalStateException("Set APP_HTTP_USER and a strong APP_HTTP_PASSWORD");
        }
        this.enabled = enabled;
        this.expected = (user + ":" + password).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        byte[] supplied;
        try {
            supplied = header != null && header.startsWith("Basic ")
                    ? Base64.getDecoder().decode(header.substring(6)) : new byte[0];
        } catch (IllegalArgumentException exception) {
            supplied = new byte[0];
        }
        if (!MessageDigest.isEqual(expected, supplied)) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"Investment Lab\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
