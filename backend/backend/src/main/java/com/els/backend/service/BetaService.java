package com.els.backend.service;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class BetaService {
    private static final RestTemplate restTemplate = new RestTemplate();

    // Newton stock-beta endpoint + fixed parameters per project spec
    private static final String BASE_URL = "https://api.newtonanalytics.com/stock-beta/";
    private static final String INDEX_PARAM = "%5EGSPC"; // URL-encoded "^GSPC"
    private static final String INTERVAL = "1mo";
    private static final int OBSERVATIONS = 12;

    public static double getBeta(String ticker) {
        String t = normalizeTicker(ticker);
        if (t.isEmpty()) return 0.00;

        // Build the Newton request URL for beta
        String url = BASE_URL
                + "?ticker=" + t
                + "&index=" + INDEX_PARAM
                + "&interval=" + INTERVAL
                + "&observations=" + OBSERVATIONS;

        try {
            // Newton returns beta in the "data" field
            BetaResponse resp = restTemplate.getForObject(url, BetaResponse.class);
            return (resp != null && resp.getData() != null) ? resp.getData() : 0.00;
        } catch (Exception e) {
            System.err.println("Failed to fetch beta for ticker=" + t + ": " + e.getMessage());
            return 0.00;
        }
    }

    // Clean ticker input (null-safe, trim spaces, uppercase)
    private static String normalizeTicker(String ticker) {
        return (ticker == null) ? "" : ticker.trim().toUpperCase();
    }
}