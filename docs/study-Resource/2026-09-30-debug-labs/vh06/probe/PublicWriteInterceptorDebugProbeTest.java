package cn.voidhaven.api.web;

import cn.voidhaven.api.service.PublicWriteRateLimiter;
import cn.voidhaven.api.service.PublicStatusQueryRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 手工调用拦截器，证明分派和异常传播；不证明 MVC 顺序和 HTTP 响应头。 */
class PublicWriteInterceptorDebugProbeTest {
    final PublicWriteRateLimiter write = mock(PublicWriteRateLimiter.class);
    final PublicStatusQueryRateLimiter query = mock(PublicStatusQueryRateLimiter.class);
    final ClientNetworkSubject network = mock(ClientNetworkSubject.class);
    final PublicWriteRateLimitInterceptor interceptor = new PublicWriteRateLimitInterceptor(write, query, network);

    @Test void post_submission_uses_write_bucket() {
        var request = new MockHttpServletRequest("POST", "/api/v1/public/submissions");
        when(network.resolve(request)).thenReturn("203.0.113.55");
        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertThat(allowed).isTrue();
        verify(write).requireAvailable("203.0.113.55");
        verifyNoInteractions(query);
    }
    @Test void post_status_uses_query_bucket() {
        var request = new MockHttpServletRequest("POST", "/api/v1/public/submissions/status-query");
        when(network.resolve(request)).thenReturn("203.0.113.55");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(query).requireAvailable("203.0.113.55");
        verifyNoInteractions(write);
    }
    @Test void get_does_not_consume_either_bucket() {
        var request = new MockHttpServletRequest("GET", "/api/v1/public/submissions");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verifyNoInteractions(write, query, network);
    }
    @Test void limiter_failure_propagates_before_prehandle_returns() {
        var request = new MockHttpServletRequest("POST", "/api/v1/public/submissions");
        when(network.resolve(request)).thenReturn("203.0.113.55");
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "PUBLIC_WRITE_RATE_LIMITED", "合成限流"))
                .when(write).requireAvailable("203.0.113.55");
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .hasFieldOrPropertyWithValue("code", "PUBLIC_WRITE_RATE_LIMITED");
        verifyNoInteractions(query);
    }
}
