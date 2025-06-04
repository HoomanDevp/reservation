package com.azki.reservation.security;

import com.azki.reservation.repository.UserRepository;
import com.azki.reservation.security.util.JwtUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends GenericFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest http = (HttpServletRequest) request;
        String authHeader = http.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.isTokenValid(token)) {
                String email = jwtUtil.extractEmail(token);
                userRepository.findByEmail(email).ifPresent(user -> {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user.getEmail(), null, List.of()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
        }

        chain.doFilter(request, response);
    }
}
