package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.interfaces.rest.support.dto.ApiErrorResponse;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorControllerUnitTest {

    @Test
    void error_404_returnsApiResponseJsonBody() {
        ApiErrorController controller = new ApiErrorController();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        req.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/does-not-exist");

        var res = controller.error(req);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getHeaders().getContentType()).isNotNull();
        assertThat(res.getHeaders().getContentType().toString()).contains("application/json");

        ApiErrorResponse body = res.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.code()).isEqualTo("NOT_FOUND");
        assertThat(body.success()).isFalse();
        assertThat(body.error().code()).isEqualTo("NOT_FOUND");
        assertThat(body.message().toLowerCase()).doesNotContain("<html");
    }

    @Test
    void error_404_productPath_returnsSameEnvelopeShape() {
        ApiErrorController controller = new ApiErrorController();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        req.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v5/does-not-exist-route");

        var res = controller.error(req);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().success()).isFalse();
        assertThat(res.getBody().error().code()).isEqualTo("NOT_FOUND");
        assertThat(res.getHeaders().getContentType().toString()).contains("application/json");
    }

    @Test
    void error_401_returnsUnauthorizedEnvelope_notHtml() {
        ApiErrorController controller = new ApiErrorController();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 401);
        req.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/auth/me");

        var res = controller.error(req);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ApiErrorResponse body = res.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.success()).isFalse();
        assertThat(body.error().code()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.message().toLowerCase()).doesNotContain("<html");
    }
}

