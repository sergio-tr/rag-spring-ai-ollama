package com.uniovi.rag.interfaces.rest.auth.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OauthExchangeRequestTest {

    @Test
    void code_returnsConstructorValue() {
        assertThat(new OauthExchangeRequest("exchange-code").code()).isEqualTo("exchange-code");
    }
}
