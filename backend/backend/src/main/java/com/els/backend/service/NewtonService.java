package com.els.backend.service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class NewtonService {
    private static final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);
        f.setReadTimeout(12_000);
        return new RestTemplate(f);
    }
    private static final double RISK_FREE_RATE = 0.04;

    public static double getExpectedReturn(String ticker){
        //uses 1 month interval and observing the last 12 months
        String url = "https://api.newtonanalytics.com/price/?ticker=" + ticker + "&interval=1mo&dataType=06&observations=12";
        //Data is organized by [UNIX TimeStamp, Adjusted Close Price];
        //Order: (Oldest -> Newest) in Months
        try{
            Response response = restTemplate.getForObject(url, Response.class);
            if(response != null && response.getData()!=null && !response.getData().isEmpty()){
                List<List<Object>> prices = response.getData();
                //gets the last price in the listings
                double lastPrice = Double.parseDouble(prices.get(0).get(1).toString());
                //gets the first price in the listings
                double firstPrice = Double.parseDouble(prices.get(prices.size()-1).get(1).toString());
                return (lastPrice - firstPrice) / firstPrice;
            }
        } catch(Exception e) {
            System.err.println("Failed to fetch Newton prices for ticker=" + ticker + ": " + e.getMessage());
        }
        return 0.00;
    }
}
