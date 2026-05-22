package com.example.seckill.repository;

import com.example.seckill.entity.ProductSku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {
    
    /** 按商品ID查询所有SKU */
    List<ProductSku> findByProductId(Long productId);
    
    /** 按商品ID和状态查询 */
    List<ProductSku> findByProductIdAndStatus(Long productId, Integer status);
}