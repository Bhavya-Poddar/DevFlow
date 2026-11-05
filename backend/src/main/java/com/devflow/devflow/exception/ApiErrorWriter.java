package com.devflow.devflow.exception;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiErrorWriter {

    private final JsonMapper objectMapper;

    public ApiErrorWriter(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApiErrorResponse response(HttpStatus status, String code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status.value(), code, message, path);
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), response(
                status, code, message, request.getRequestURI()));
    }
}
