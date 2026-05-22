package com.example.seckill.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品实体 - SPU概念
 * 一个SPU下有多个SKU
 */
@Entity
@Table(name = "t_product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SPU名称（如"水果礼盒"） */
    @Column(name = "name")
    private String name;

    /** 分类 */
    @Column(name = "category")
    private String category;

    /** 是否开启秒杀 */
    @Column(name = "seckill_enabled")
    private Boolean seckillEnabled;

    /** 秒杀开始时间 */
    @Column(name = "seckill_start_time")
    private LocalDateTime seckillStartTime;

    /** 秒杀结束时间 */
    @Column(name = "seckill_end_time")
    private LocalDateTime seckillEndTime;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Boolean getSeckillEnabled() { return seckillEnabled; }
    public void setSeckillEnabled(Boolean seckillEnabled) { this.seckillEnabled = seckillEnabled; }
    public LocalDateTime getSeckillStartTime() { return seckillStartTime; }
    public void setSeckillStartTime(LocalDateTime seckillStartTime) { this.seckillStartTime = seckillStartTime; }
    public LocalDateTime getSeckillEndTime() { return seckillEndTime; }
    public void setSeckillEndTime(LocalDateTime seckillEndTime) { this.seckillEndTime = seckillEndTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}