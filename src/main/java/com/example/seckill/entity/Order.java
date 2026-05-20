package com.example.seckill.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", unique = true)
    private String orderNo;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Order() {}

    public static OrderBuilder builder() {
        return new OrderBuilder();
    }

    public static class OrderBuilder {
        private String orderNo;
        private Long userId;
        private Long skuId;
        private Integer quantity;
        private String status;
        private LocalDateTime createdAt;

        public OrderBuilder orderNo(String orderNo) { this.orderNo = orderNo; return this; }
        public OrderBuilder userId(Long userId) { this.userId = userId; return this; }
        public OrderBuilder skuId(Long skuId) { this.skuId = skuId; return this; }
        public OrderBuilder quantity(Integer quantity) { this.quantity = quantity; return this; }
        public OrderBuilder status(String status) { this.status = status; return this; }
        public OrderBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public Order build() {
            Order order = new Order();
            order.orderNo = this.orderNo;
            order.userId = this.userId;
            order.skuId = this.skuId;
            order.quantity = this.quantity;
            order.status = this.status;
            order.createdAt = this.createdAt;
            return order;
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}