package com.els.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BetaResponseTest {

    @Test
    void defaultDataIsNull() {
        BetaResponse response = new BetaResponse();
        assertNull(response.getData());
    }

    @Test
    void setData_roundTripsValue() {
        BetaResponse response = new BetaResponse();
        response.setData(1.25);
        assertEquals(1.25, response.getData());
    }
}
