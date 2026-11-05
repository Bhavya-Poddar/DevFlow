package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.controller.AuthController;
import com.devflow.devflow.service.AuthService;

class AuthControllerRegistrationTests {
    @Test
    void publicRegistrationIsRejectedWithoutCreatingAnAccount() {
        AuthService auth = mock(AuthService.class);
        AuthController controller = new AuthController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "authService", auth);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> controller.registerUser(Map.of("name", "Unexpected", "role", "ADMIN")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(auth);
    }
}
