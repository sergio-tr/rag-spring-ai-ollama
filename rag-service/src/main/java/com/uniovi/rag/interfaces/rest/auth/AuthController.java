package com.uniovi.rag.interfaces.rest.auth;

import com.uniovi.rag.application.usecase.auth.AuthService;
import com.uniovi.rag.interfaces.rest.auth.dto.ConfirmEmailRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.ForgotPasswordRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.RefreshRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.ResendConfirmationRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest body) {
        return authService.login(body);
    }

    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest body) {
        return authService.register(body);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest body) {
        return authService.refresh(body);
    }

    @PostMapping("/confirm-email")
    public void confirmEmail(@Valid @RequestBody ConfirmEmailRequest body) {
        authService.confirmEmail(body);
    }

    @PostMapping("/resend-confirmation")
    public void resendConfirmation(@Valid @RequestBody ResendConfirmationRequest body) {
        authService.resendConfirmation(body);
    }

    @PostMapping("/forgot-password")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        authService.forgotPassword(body);
    }

    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        authService.resetPassword(body);
    }
}
