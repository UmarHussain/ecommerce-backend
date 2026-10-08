package com.umar.ecommerce.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DownstreamStatusTest {

    @Test
    void mapsTransportFailuresWithoutRewritingOrdinaryErrors() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, DownstreamStatus.resolve(new ConnectException("refused")));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, DownstreamStatus.resolve(new TimeoutException("slow")));
        assertEquals(HttpStatus.BAD_GATEWAY, DownstreamStatus.resolve(new java.io.IOException("reset")));
        assertNull(DownstreamStatus.resolve(new IllegalStateException("local")));
    }
}
