package com.example.seckill.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU实体 - 具体商品规格
 * 如：苹果-山东红富士-12个装、脐橙-江西-24个装
 */
@Entity
@Table(name = "t_product_sku")
public class ProductSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联SPU */
    @Column(name = "product_id")
    private Long productId;

    /** SKU名称（如"山东红富士苹果12个装"） */
    @Column(name = "sku_name")
    private String skuName;

    /** 规格（如"12个装"） */
    @Column(name = "spec")
    private String spec;

    /** 产地 */
    @Column(name = "origin")
    private String origin;

    /** 单价 */
    @Column(name = "price")
    private BigDecimal price;

    /** 秒杀价 */
    @Column(name = "seckill_price")
    private BigDecimal seckillPrice;

    /** 总库存 */
    @Column(name = "stock")
    private Integer stock;

    /** 预留库存（秒杀外） */
    @Column(name = "reserved_stock")
    private Integer reservedStock;

    /** 锁定库存（订单占用） */
    @Column(name = "locked_stock")
    private Integer lockedStock;

    /** 可秒杀库存（stock - reservedStock） */
    @Transient
    private Integer seckillStock;

    /** 状态：0-禁用 1-启用 */
    @Column(name = "status")
    private Integer status;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getSeckillPrice() { return seckillPrice; }
    public void setSeckillPrice(BigDecimal seckillPrice) { this.seckillPrice = seckillPrice; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Integer getReservedStock() { return reservedStock; }
    public void setReservedStock(Integer reservedStock) { this.reservedStock = reservedStock; }
    public Integer getLockedStock() { return lockedStock; }
    public void setLockedStock(Integer lockedStock) { this.lockedStock = lockedStock; }
    public Integer getSeckillStock() { 
        return stock - reservedStock - lockedStock; 
    }
    public void setSeckillStock(Integer seckillStock) { 
        this.seckillStock = seckillStock; 
    }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}