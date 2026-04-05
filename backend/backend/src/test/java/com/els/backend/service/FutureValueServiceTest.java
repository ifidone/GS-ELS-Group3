package com.els.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FutureValueServiceTest {

    @Test
    void computeFutureValue_invalidInput_returnsZeros() {
        FutureValueService.FutureValueComputation result =
                FutureValueService.computeFutureValue("", -100.0, 0.0);

        assertEquals(0.0, result.beta(), 0.0001);
        assertEquals(0.0, result.expectedReturn(), 0.0001);
        assertEquals(0.0, result.futureValue(), 0.0001);
    }

    @Test
    void computeFutureValue_happyPath_usesExternalData() throws Exception {
        MockRestServiceServer betaServer = MockRestServiceServer.createServer(getBetaRestTemplate());
        MockRestServiceServer newtonServer = MockRestServiceServer.createServer(getNewtonRestTemplate());

        String betaUrl = "https://api.newtonanalytics.com/stock-beta/"
                + "?ticker=VFIAX&index=%255EGSPC&interval=1mo&observations=12";
        betaServer.expect(requestTo(betaUrl))
                .andRespond(withSuccess("{\"data\":1.2}", MediaType.APPLICATION_JSON));

        String newtonUrl = "https://api.newtonanalytics.com/price/?ticker=VFIAX&interval=1mo&dataType=06&observations=12";
        String newtonResponse = "{\"data\":[[0,110.0],[1,100.0]]}";
        newtonServer.expect(requestTo(newtonUrl))
                .andRespond(withSuccess(newtonResponse, MediaType.APPLICATION_JSON));

        FutureValueService.FutureValueComputation result =
                FutureValueService.computeFutureValue("vfiax", 1000.0, 5.0);

        double expectedReturn = (110.0 - 100.0) / 100.0;
        double capmRate = Math.max(0.04, 0.04 + 1.2 * (expectedReturn - 0.04));
        double expectedFutureValue = 1000.0 * Math.exp(capmRate * 5.0);

        assertEquals(1.2, result.beta(), 0.0001);
        assertEquals(expectedReturn, result.expectedReturn(), 0.0001);
        assertEquals(expectedFutureValue, result.futureValue(), 0.0001);

        betaServer.verify();
        newtonServer.verify();
    }

    @Test
    void computeCapmRate_clampsToRiskFreeRate() {
        double capmRate = FutureValueService.computeCapmRate(-0.5, -0.2);
        assertEquals(0.04, capmRate, 0.0001);
    }

    private RestTemplate getBetaRestTemplate() throws Exception {
        Field field = BetaService.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        return (RestTemplate) field.get(null);
    }

    private RestTemplate getNewtonRestTemplate() throws Exception {
        Field field = NewtonService.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        return (RestTemplate) field.get(null);
    }
}
