package com.devflow.devflow.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/** Bounds webhook request reads before Spring MVC can materialize a byte[] body. */
@Component
@ConditionalOnProperty(prefix = "devflow.github", name = "enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class GitHubWebhookRequestSizeFilter extends OncePerRequestFilter {
    private static final String WEBHOOK_PATH = "/api/integrations/github/webhook";
    private final int maxRequestBytes;

    public GitHubWebhookRequestSizeFilter(
            @Value("${devflow.github.webhook.max-request-bytes:1048576}") int maxRequestBytes) {
        if (maxRequestBytes <= 0) {
            throw new IllegalArgumentException("GitHub webhook maximum request size must be positive");
        }
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equalsIgnoreCase(request.getMethod()) || !WEBHOOK_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            jakarta.servlet.FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxRequestBytes) {
            sendPayloadTooLarge(response);
            return;
        }

        byte[] boundedBody = readBoundedBody(request.getInputStream());
        if (boundedBody == null) {
            sendPayloadTooLarge(response);
            return;
        }

        filterChain.doFilter(new ReplayableBodyRequest(request, boundedBody), response);
    }

    /** Returns null as soon as maxRequestBytes + 1 bytes prove the body is oversized. */
    private byte[] readBoundedBody(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxRequestBytes, 8192));
        byte[] chunk = new byte[8192];
        int total = 0;
        while (true) {
            int allowedRead = (int) Math.min(chunk.length, (long) maxRequestBytes - total + 1L);
            int read = input.read(chunk, 0, allowedRead);
            if (read < 0) return buffer.toByteArray();
            if (read == 0) continue;
            if (read > maxRequestBytes - total) return null;
            buffer.write(chunk, 0, read);
            total += read;
        }
    }

    private void sendPayloadTooLarge(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "GitHub payload is too large");
    }

    private static final class ReplayableBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private ReplayableBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("Asynchronous reads are not supported for webhook bodies");
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            Charset charset = getCharacterEncoding() == null
                ? Charset.defaultCharset() : Charset.forName(getCharacterEncoding());
            return new java.io.BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
