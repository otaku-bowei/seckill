package com.example.seckill.dto;

import java.io.Serializable;

/**
 * 订单消息对象
 */
public class OrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderNo;
    private Long userId;
    private Long skuId;
    private Integer quantity;
    private String status;
    private Long timestamp;

    public OrderMessage() {}

    public OrderMessage(String orderNo, Long userId, Long skuId, Integer quantity, String status, Long timestamp) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.skuId = skuId;
        this.quantity = quantity;
        this.status = status;
        this.timestamp = timestamp;
    }

    public static OrderMessageBuilder builder() {
        return new OrderMessageBuilder();
    }

    public static class OrderMessageBuilder {
        private String orderNo;
        private Long userId;
        private Long skuId;
        private Integer quantity;
        private String status;
        private Long timestamp;

        public OrderMessageBuilder orderNo(String orderNo) { this.orderNo = orderNo; return this; }
        public OrderMessageBuilder userId(Long userId) { this.userId = userId; return this; }
        public OrderMessageBuilder skuId(Long skuId) { this.skuId = skuId; return this; }
        public OrderMessageBuilder quantity(Integer quantity) { this.quantity = quantity; return this; }
        public OrderMessageBuilder status(String status) { this.status = status; return this; }
        public OrderMessageBuilder timestamp(Long timestamp) { this.timestamp = timestamp; return this; }

        public OrderMessage build() {
            return new OrderMessage(orderNo, userId, skuId, quantity, status, timestamp);
        }
    }

    // Getters and Setters
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}