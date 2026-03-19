package com.els.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NewtonServiceTest {

    @Test
    void getExpectedReturn_returnsComputedValue() throws Exception {
        RestTemplate restTemplate = getRestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        String url = "https://api.newtonanalytics.com/price/?ticker=VFIAX&interval=1mo&dataType=06&observations=12";
        String response = "{\"data\":[[0,120.0],[1,100.0]]}";
        server.expect(requestTo(url))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        double expectedReturn = NewtonService.getExpectedReturn("VFIAX");
        assertEquals(0.2, expectedReturn, 0.0001);

        server.verify();
    }

    @Test
    void getExpectedReturn_emptyData_returnsZero() throws Exception {
        RestTemplate restTemplate = getRestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        String url = "https://api.newtonanalytics.com/price/?ticker=VFIAX&interval=1mo&dataType=06&observations=12";
        server.expect(requestTo(url))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        double expectedReturn = NewtonService.getExpectedReturn("VFIAX");
        assertEquals(0.0, expectedReturn, 0.0001);

        server.verify();
    }

    private RestTemplate getRestTemplate() throws Exception {
        Field field = NewtonService.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        return (RestTemplate) field.get(null);
    }
}
