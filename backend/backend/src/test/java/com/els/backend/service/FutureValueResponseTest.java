package com.els.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FutureValueResponseTest {

    @Test
    void defaultFutureValueIsNull() {
        FutureValueResponse response = new FutureValueResponse();
        assertNull(response.getFutureValue());
    }

    @Test
    void setFutureValue_roundTripsValue() {
        FutureValueResponse response = new FutureValueResponse();
        response.setFutureValue(2500.75);
        assertEquals(2500.75, response.getFutureValue());
    }
}
