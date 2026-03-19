package com.els.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BetaServiceTest {

    @Test
    void getBeta_nullTicker_returnsZero() {
        double beta = BetaService.getBeta(null);
        assertEquals(0.0, beta, 0.0001);
    }

    @Test
    void getBeta_blankTicker_returnsZero() {
        double beta = BetaService.getBeta("   ");
        assertEquals(0.0, beta, 0.0001);
    }

    @Test
    void getBeta_returnsDataFromResponse() throws Exception {
        RestTemplate restTemplate = getRestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        String url = "https://api.newtonanalytics.com/stock-beta/"
                + "?ticker=VFIAX&index=%255EGSPC&interval=1mo&observations=12";
        server.expect(requestTo(url))
                .andRespond(withSuccess("{\"data\":1.23}", MediaType.APPLICATION_JSON));

        double beta = BetaService.getBeta("vfiax");
        assertEquals(1.23, beta, 0.0001);

        server.verify();
    }

    private RestTemplate getRestTemplate() throws Exception {
        Field field = BetaService.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        return (RestTemplate) field.get(null);
    }
}
