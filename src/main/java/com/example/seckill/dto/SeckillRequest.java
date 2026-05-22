package com.example.seckill.dto;

import java.util.List;

public class SeckillRequest {

    private Long userId;
    private Long productId;    // SPU ID
    private Long skuId;        // 单个SKU
    private List<Long> skuIds;   // 多个SKU
    private Integer quantity = 1;

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public List<Long> getSkuIds() { return skuIds; }
    public void setSkuIds(List<Long> skuIds) { this.skuIds = skuIds; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}