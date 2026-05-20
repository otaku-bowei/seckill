package com.example.seckill.dto;

/**
 * 抢购响应
 */
public class SeckillResponse {

    private int code;
    private String message;
    private String data;

    public SeckillResponse() {}

    public SeckillResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public SeckillResponse(int code, String message, String data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}