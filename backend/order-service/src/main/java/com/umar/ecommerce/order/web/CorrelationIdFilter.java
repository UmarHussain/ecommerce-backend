package com.umar.ecommerce.order.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String current = request.getHeader(HEADER);
        if (current == null || current.isBlank()) {
            current = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER, current);
        filterChain.doFilter(request, response);
    }

    public static String correlationId(HttpServletRequest request) {
        String current = request.getHeader(HEADER);
        return current == null || current.isBlank() ? "order" : current;
    }
}
