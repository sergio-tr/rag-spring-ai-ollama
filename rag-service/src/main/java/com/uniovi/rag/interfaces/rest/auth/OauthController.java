package com.uniovi.rag.interfaces.rest.auth;

import com.uniovi.rag.application.service.auth.OauthLoginService;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.OauthExchangeRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Google OAuth HTTP adapter.
 *
 * <p>Contract: {@code {productBase}/auth/oauth/**} (default {@code /api/v5/auth/oauth/**}):
 * {@code GET .../google/start}, {@code GET .../google/callback}, {@code POST .../exchange}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/auth/oauth")
public class OauthController {

    private final OauthLoginService oauthLoginService;

    public OauthController(OauthLoginService oauthLoginService) {
        this.oauthLoginService = oauthLoginService;
    }

    @GetMapping("/google/start")
    public void startGoogle(
            @RequestParam(name = "locale", required = false) String locale,
            HttpServletResponse response) throws IOException {
        response.sendRedirect(oauthLoginService.googleStartUrl(locale));
    }

    @GetMapping("/google/callback")
    public void callbackGoogle(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            HttpServletResponse response) throws IOException {
        String redirect = oauthLoginService.handleGoogleCallback(code, state, error);
        response.sendRedirect(redirect);
    }

    @PostMapping("/exchange")
    public LoginResponse exchange(@Valid @RequestBody OauthExchangeRequest body) {
        return oauthLoginService.exchange(body.code());
    }
}

