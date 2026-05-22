package com.example.seckill.service;

import com.example.seckill.constant.GlobalConstants;
import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * 基于 SETNX 的秒杀实现
 * 
 * 对比说明：
 * - 优势：代码直观，适合简单场景
 * - 劣势：多key操作非原子，并发时有竞态条件，可能超卖
 */
@Service
public class SeckillServiceSETNX {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 采用 SETNX 的扣减
     * 
     * 原理：
     * 1. 先尝试获取"用户购买权"（SETNX互斥）
     * 2. 成功则扣减库存
     * 3. 失败则返回已抢购
     * 
     * 问题：步骤2和3之间有时间窗口，高并发可能超卖
     */
    public SeckillResponse seckillBySETNX(SeckillRequest request) {
        Long userId = request.getUserId();
        Long skuId = request.getSkuId();

        String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
        String userKey = GlobalConstants.USER_BUY_KEY + skuId + ":" + userId;

        // 1. 尝试获取用户购买权（SETNX原子操作）
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(userKey, "1");

        // 2. 获取失败 = 已抢购过
        if (acquired == null || !acquired) {
            return new SeckillResponse(400, "您已抢购过");
        }

        // 3. 扣减库存 ← 非原子！有时间窗口
        Long newStock = redisTemplate.opsForValue().decrement(stockKey);

        // 4. 库存不足，回滚
        if (newStock == null || newStock < 0) {
            redisTemplate.delete(userKey);
            redisTemplate.opsForValue().increment(stockKey);
            return new SeckillResponse(402, "商品已抢完");
        }

        // 5. 抢购成功
        String orderNo = generateOrderNo(userId, skuId);
        return new SeckillResponse(200, "抢购成功", orderNo);
    }

    /**
     * 另一种SETNX实现：库存锁（问题更大）
     * 
     * 问题是：GET + DECR 不是原子操作
     * 可能导致超卖
     */
    public SeckillResponse seckillByStockLock(SeckillRequest request) {
        Long skuId = request.getSkuId();
        String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
        String lockKey = "sku:lock:" + skuId;

        // 1. 尝试获取库存锁
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(10));

        if (locked == null || !locked) {
            return new SeckillResponse(500, "系统繁忙");
        }

        try {
            // 2. 扣减库存（非原子！）
            String stockStr = redisTemplate.opsForValue().get(stockKey);
            long stock = stockStr == null ? 0 : Long.parseLong(stockStr);

            if (stock <= 0) {
                return new SeckillResponse(402, "商品已抢完");
            }

            redisTemplate.opsForValue().decrement(stockKey);
            return new SeckillResponse(200, "抢购成功");

        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 订单号生成
     */
    private String generateOrderNo(Long userId, Long skuId) {
        return String.format("%d%d-%d", userId, skuId, 
                System.currentTimeMillis() + new Random().nextInt(1000));
    }

    /**
     * 初始化库存���供测试调用）
     */
    public void initStock(Long skuId, Integer stock) {
        String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
    }
}