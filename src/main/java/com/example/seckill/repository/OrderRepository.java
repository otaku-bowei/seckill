package com.example.seckill.repository;

import com.example.seckill.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    Optional<Order> findByOrderNo(String orderNo);
    
    /** 统计SKU的总订单数 */
    int countBySkuId(Long skuId);
    
    /** 统计SKU的已付款订单数 */
    int countBySkuIdAndStatus(Long skuId, String status);
}