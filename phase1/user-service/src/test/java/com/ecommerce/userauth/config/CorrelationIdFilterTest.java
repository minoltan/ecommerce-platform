package com.ecommerce.userauth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void generatesACorrelationIdWhenHeaderIsAbsent() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).setHeader(eq(CorrelationIdFilter.HEADER), any());
        verify(chain).doFilter(request, response);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void echoesACallerSuppliedCorrelationId() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("caller-correlation-id");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(CorrelationIdFilter.HEADER, "caller-correlation-id");
        verify(chain).doFilter(request, response);
    }

    @Test
    void populatesMdcWhileTheChainExecutesAndClearsItAfterwards() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("mdc-correlation-id");
        doAnswer(invocation -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("mdc-correlation-id");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesABlankCorrelationIdWhenHeaderIsBlank() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("  ");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(eq(CorrelationIdFilter.HEADER), any());
        verify(chain).doFilter(request, response);
    }
}
