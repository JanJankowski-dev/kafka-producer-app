package com.example.producer.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivateHttpFilterTest {
    @Test
    void deploymentCannotStartWithoutAuthentication() {
        assertThatThrownBy(() -> new PrivateHttpFilter(false, true, "", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PrivateHttpFilter(true, true, "admin", "short"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void protectsEveryRouteWhenEnabled() throws Exception {
        var filter = new PrivateHttpFilter(true, true, "owner", "a-long-private-password-123");
        var anonymous = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/"), anonymous, new MockFilterChain());
        assertThat(anonymous.getStatus()).isEqualTo(401);

        var request = new MockHttpServletRequest("GET", "/daily-research/valuations");
        request.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                "owner:a-long-private-password-123".getBytes(StandardCharsets.UTF_8)));
        var accepted = new MockHttpServletResponse();
        filter.doFilter(request, accepted, new MockFilterChain());
        assertThat(accepted.getStatus()).isEqualTo(200);
    }
}
