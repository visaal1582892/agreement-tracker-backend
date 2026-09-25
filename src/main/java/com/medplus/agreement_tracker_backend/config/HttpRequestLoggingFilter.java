package com.medplus.agreement_tracker_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/actuator") || uri.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 10000);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);

        long startMs = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } catch (Exception ex) {
            log.error("HTTP {} {} failed before response (requestId={})", request.getMethod(), buildUri(request), requestId, ex);
            throw ex;
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            logHttpOutcome(wrappedRequest, wrappedResponse, durationMs, requestId);
            wrappedResponse.copyBodyToResponse();
            MDC.remove("requestId");
        }
    }

    private void logHttpOutcome(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long durationMs, String requestId) {
        int status = response.getStatus();
        String method = request.getMethod();
        String uri = buildUri(request);
        String user = resolveUsername();

        if (status >= 500) {
            String reqBody = getPayload(request.getContentAsByteArray(), request.getCharacterEncoding());
            String resBody = getPayload(response.getContentAsByteArray(), response.getCharacterEncoding());
            log.error("HTTP {} {} -> {} {}ms user={} requestId={}. Request: [{}], Response: [{}]", method, uri, status, durationMs, user, requestId, reqBody, resBody);
        } else if (status >= 400) {
            String reqBody = getPayload(request.getContentAsByteArray(), request.getCharacterEncoding());
            String resBody = getPayload(response.getContentAsByteArray(), response.getCharacterEncoding());
            log.warn("HTTP {} {} -> {} {}ms user={} requestId={}. Request: [{}], Response: [{}]", method, uri, status, durationMs, user, requestId, reqBody, resBody);
        } else if (log.isDebugEnabled()) {
            log.debug("HTTP {} {} -> {} {}ms user={} requestId={}", method, uri, status, durationMs, user, requestId);
        } else {
            log.info("HTTP {} {} -> {} {}ms user={}", method, uri, status, durationMs, user);
        }
    }

    private String getPayload(byte[] buf, String characterEncoding) {
        if (buf.length > 0) {
            try {
                return new String(buf, 0, Math.min(buf.length, 10000), characterEncoding);
            } catch (UnsupportedEncodingException ex) {
                return "[Unsupported Encoding]";
            }
        }
        return "";
    }

    private String buildUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (StringUtils.hasText(request.getQueryString())) {
            return uri + "?" + request.getQueryString();
        }
        return uri;
    }

    private String resolveUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        String name = authentication.getName();
        return StringUtils.hasText(name) ? name : "anonymous";
    }
}
