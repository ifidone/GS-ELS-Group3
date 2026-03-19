package com.els.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ResponseTest {

    @Test
    void defaultDataIsNull() {
        Response response = new Response();
        assertNull(response.getData());
    }

    @Test
    void setData_roundTripsValue() {
        Response response = new Response();
        List<List<Object>> data = List.of(List.of(1, "VFIAX"), List.of(2, 100.0));
        response.setData(data);

        assertSame(data, response.getData());
        assertEquals(2, response.getData().size());
    }
}
