package com.example.seckill.service;

import com.example.seckill.constant.GlobalConstants;
import com.example.seckill.constant.LogCode;
import com.example.seckill.constant.LogUtils;
import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 基于Redisson分布式锁的秒杀实现
 */
@Service
public class SeckillServiceRedisson {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "seckill:lock:";
    private static final long WAIT_TIME = 3;   // 等待获取锁最多3秒
    private static final long LEASE_TIME = 10;   // 自动释放锁最多10秒

    /**
     * Redisson分布式锁实现
     * 
     * 流程：
     * 1. 获取分布式锁 → 保证同一SKU请求串行化
     * 2. 检查用户是否已抢购
     * 3. 检查并扣减库存
     * 4. 释放锁
     */
    public SeckillResponse seckillByRedisson(SeckillRequest request) {
        Long userId = request.getUserId();
        Long skuId = request.getSkuId();
        Integer quantity = request.getQuantity();

        String lockKey = LOCK_PREFIX + skuId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 1. 尝试获取锁，最多等3秒
            boolean acquired = lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS);
            
            if (!acquired) {
                return new SeckillResponse(500, "系统繁忙，请稍后重试");
            }

            // 2. 检查用户是否已抢购
            String userKey = GlobalConstants.USER_BUY_KEY + skuId + ":" + userId;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(userKey))) {
                LogUtils.log(LogCode.S004, userId);
                return new SeckillResponse(400, "您已抢购过");
            }

            // 3. 检查库存
            String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
            String stockStr = redisTemplate.opsForValue().get(stockKey);
            int stock = stockStr == null ? 0 : Integer.parseInt(stockStr);

            if (stock < quantity) {
                LogUtils.log(LogCode.S003, skuId);
                return new SeckillResponse(402, "商品已抢完");
            }

            // 4. 扣减库存
            redisTemplate.opsForValue().decrement(stockKey, quantity);
            redisTemplate.opsForValue().set(userKey, String.valueOf(quantity));
            // 设置过期时间
            redisTemplate.expire(userKey, 24, TimeUnit.HOURS);

            LogUtils.log(LogCode.S002, userId);
            return new SeckillResponse(200, "抢购成功");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SeckillResponse(500, "系统错误");
        } catch (Exception e) {
            return new SeckillResponse(500, "系统错误: " + e.getMessage());
        } finally {
            // 5. 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 简化版：使用tryLock自动重试
     */
    public SeckillResponse seckillSimple(SeckillRequest request) {
        Long userId = request.getUserId();
        Long skuId = request.getSkuId();
        Integer quantity = request.getQuantity();

        String lockKey = LOCK_PREFIX + skuId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 等待获取锁，重试间隔100ms
            lock.lock(LEASE_TIME, TimeUnit.SECONDS);

            // 以下代码同一SKU只会串行执行
            String userKey = GlobalConstants.USER_BUY_KEY + skuId + ":" + userId;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(userKey))) {
                return new SeckillResponse(400, "您已抢购过");
            }

            String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
            Long stock = redisTemplate.opsForValue().decrement(stockKey, quantity);

            if (stock == null || stock < 0) {
                // 回滚库存
                redisTemplate.opsForValue().increment(stockKey, quantity);
                return new SeckillResponse(402, "商品已抢完");
            }

            redisTemplate.opsForValue().set(userKey, String.valueOf(quantity), 24, TimeUnit.HOURS);
            return new SeckillResponse(200, "抢购成功");

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}