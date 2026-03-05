package com.els.backend.service;
import java.util.List;
public class Response {
    private int status;
    private String message;
    private List<List<Object>> data;

    public List<List<Object>> getData(){
        return data;
    }

    public void setData(List<List<Object>> data){
        this.data = data;
    }
}
