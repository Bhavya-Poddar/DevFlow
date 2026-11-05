package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.exception.ApiErrorResponse;
import com.devflow.devflow.exception.ApiExceptionHandler;

class ApiExceptionHandlerTests {

    private ApiExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new ApiExceptionHandler();
        request = new MockHttpServletRequest("GET", "/api/issues/42");
    }

    @Test
    void preservesIntentionalResponseStatusAndUsesConsistentEnvelope() {
        var response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"), request);

        assertEquals(404, response.getStatusCode().value());
        assertEquals("NOT_FOUND", response.getBody().errorCode());
        assertEquals("Issue not found", response.getBody().message());
        assertEquals("/api/issues/42", response.getBody().path());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void validationErrorHasBadRequestStatusAndDoesNotEchoSubmittedValues() {
        var response = handler.handleValidation(request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("VALIDATION_ERROR", response.getBody().errorCode());
        assertEquals("Request validation failed.", response.getBody().message());
    }

    @Test
    void malformedBodyAndInvalidParametersHaveBadRequestEnvelope() {
        var response = handler.handleInvalidRequest(request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_REQUEST", response.getBody().errorCode());
    }

    @Test
    void persistenceConflictDoesNotLeakDatabaseDetails() {
        var response = handler.handleConflict(request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("DATA_CONFLICT", response.getBody().errorCode());
        assertEquals("The request conflicts with existing data.", response.getBody().message());
    }

    @Test
    void unexpectedErrorsReturnGenericMessage() {
        var exception = new IllegalStateException("database password and SQL details");
        var response = handler.handleUnexpected(exception, request);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", response.getBody().errorCode());
        assertEquals("An unexpected server error occurred.", response.getBody().message());
    }

    @Test
    void accessDeniedHasForbiddenEnvelope() {
        var response = handler.handleAccessDenied(request);
        assertEquals(403, response.getStatusCode().value());
        assertEquals("FORBIDDEN", response.getBody().errorCode());
    }

    @Test
    void notFoundHasSafeEnvelope() {
        var response = handler.handleNotFound(request);
        assertEquals(404, response.getStatusCode().value());
        assertEquals("NOT_FOUND", response.getBody().errorCode());
    }

    @Test
    void methodErrorsKeepTheirExistingStatusCategory() {
        var response = handler.handleMethodNotAllowed(request);
        assertEquals(405, response.getStatusCode().value());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().errorCode());
    }
}
