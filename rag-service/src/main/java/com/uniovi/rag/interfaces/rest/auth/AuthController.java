package com.uniovi.rag.interfaces.rest.auth;

import com.uniovi.rag.application.usecase.auth.AuthService;
import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.interfaces.rest.auth.dto.ConfirmEmailRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.ForgotPasswordRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.MeResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.RefreshRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.ResendConfirmationRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.ResetPasswordRequest;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserAccountPort userAccountPort;

    public AuthController(AuthService authService, UserAccountPort userAccountPort) {
        this.authService = authService;
        this.userAccountPort = userAccountPort;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest body) {
        return authService.login(body);
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest body) {
        RegisterResponse res = authService.register(body);
        if ("PENDING_EMAIL_VERIFICATION".equals(res.status())) {
            return ResponseEntity.accepted().body(res);
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest body) {
        return authService.refresh(body);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal RagPrincipal principal) {
        UUID userId = principal != null ? principal.userId() : null;
        if (userId == null) {
            throw new InvalidCredentialsException();
        }
        UserEntity u = userAccountPort.findById(userId).orElseThrow(InvalidCredentialsException::new);
        return MeResponse.fromUser(u);
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
