package com.els.backend.controller;

import com.els.backend.service.BetaService;
import com.els.backend.service.NewtonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CalculatorControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CalculatorController controller = new CalculatorController();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addPlaceholderValue("app.frontend-origin", "http://localhost")
                .build();
    }

    @Test
    void project_invalidInput_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/calculator/project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"\",\"initialInvestment\":1000,\"years\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void project_validInput_returnsProjection() throws Exception {
        MockRestServiceServer betaServer = MockRestServiceServer.createServer(getBetaRestTemplate());
        MockRestServiceServer newtonServer = MockRestServiceServer.createServer(getNewtonRestTemplate());

        String betaUrl = "https://api.newtonanalytics.com/stock-beta/"
                + "?ticker=AAPL&index=%255EGSPC&interval=1mo&observations=12";
        betaServer.expect(requestTo(betaUrl))
                .andRespond(withSuccess("{\"data\":1.2}", MediaType.APPLICATION_JSON));

        String newtonUrl = "https://api.newtonanalytics.com/price/?ticker=AAPL&interval=1mo&dataType=06&observations=12";
        String newtonResponse = "{\"data\":[[0,110.0],[1,100.0]]}";
        newtonServer.expect(requestTo(newtonUrl))
                .andRespond(withSuccess(newtonResponse, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/calculator/project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"aapl\",\"initialInvestment\":1000,\"years\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.beta").isNumber())
                .andExpect(jsonPath("$.expectedReturn").isNumber())
                .andExpect(jsonPath("$.futureValue").isNumber());

        betaServer.verify();
        newtonServer.verify();
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
