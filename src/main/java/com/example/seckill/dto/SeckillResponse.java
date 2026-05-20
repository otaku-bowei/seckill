package com.example.seckill.dto;

public class SeckillResponse {
    private int code;
    private String msg;
    private String orderNo;

    public SeckillResponse() {}

    public SeckillResponse(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public SeckillResponse(int code, String msg, String orderNo) {
        this.code = code;
        this.msg = msg;
        this.orderNo = orderNo;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public static SeckillResponseBuilder builder() {
        return new SeckillResponseBuilder();
    }

    public static class SeckillResponseBuilder {
        private int code;
        private String msg;
        private String orderNo;

        public SeckillResponseBuilder code(int code) {
            this.code = code;
            return this;
        }

        public SeckillResponseBuilder msg(String msg) {
            this.msg = msg;
            return this;
        }

        public SeckillResponseBuilder orderNo(String orderNo) {
            this.orderNo = orderNo;
            return this;
        }

        public SeckillResponse build() {
            return new SeckillResponse(code, msg, orderNo);
        }
    }
}