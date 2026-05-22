package com.example.seckill.service;

import com.example.seckill.constant.GlobalConstants;
import com.example.seckill.repository.OrderRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 库存对账服务
 * 定期检查Redis库存与MySQL订单是否一致，并校正
 */
@Service
public class ReconcileService {

    private static final Logger log = LoggerFactory.getLogger(ReconcileService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "seckill:reconcile:";
    private static final String INIT_STOCK_KEY = "sku:initial:stock:";

    /**
     * 记录初始库存（秒杀开始前调用）
     */
    public void recordInitialStock(Long skuId, Integer stock) {
        redisTemplate.opsForValue().set(INIT_STOCK_KEY + skuId, String.valueOf(stock));
        log.info("[初始库存记录] skuId={}, stock={}", skuId, stock);
    }

    /**
     * 对账并校正单个SKU
     */
    public Map<String, Object> reconcile(Long skuId) {
        Map<String, Object> result = new HashMap<>();
        
        String lockKey = LOCK_PREFIX + skuId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!acquired) {
                result.put("status", "ERROR");
                result.put("message", "无法获取对账锁");
                return result;
            }

            // 1. Redis剩余库存
            String redisStockStr = redisTemplate.opsForValue().get(GlobalConstants.SKU_STOCK_KEY + skuId);
            int redisStock = redisStockStr == null ? 0 : Integer.parseInt(redisStockStr);

            // 2. MySQL已付款订单数
            int paidCount = orderRepository.countBySkuIdAndStatus(skuId, "SUCCESS");

            // 3. 初始库存
            String initStockStr = redisTemplate.opsForValue().get(INIT_STOCK_KEY + skuId);
            int initialStock = initStockStr == null ? 0 : Integer.parseInt(initStockStr);

            // 4. 应该剩余的库存
            int expectedStock = initialStock - paidCount;

            // 5. 比较并校正
            if (redisStock != expectedStock) {
                redisTemplate.opsForValue().set(
                    GlobalConstants.SKU_STOCK_KEY + skuId, 
                    String.valueOf(expectedStock)
                );
                log.warn("[库存校正] skuId={}, Redis库存={}, MySQL订单={}, 初始库存={}, 校正后={}",
                    skuId, redisStock, paidCount, initialStock, expectedStock);
                
                result.put("corrected", true);
                result.put("before", redisStock);
                result.put("after", expectedStock);
            } else {
                result.put("corrected", false);
                log.info("[对账一致] skuId={}, 库存={}, 订单数={}", skuId, redisStock, paidCount);
            }

            result.put("status", "OK");
            result.put("skuId", skuId);
            result.put("initialStock", initialStock);
            result.put("paidOrders", paidCount);
            result.put("currentStock", expectedStock);

        } catch (Exception e) {
            log.error("[对账异常] skuId={}, error={}", skuId, e.getMessage());
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        return result;
    }

    /**
     * 对账所有SKU
     */
    public Map<String, Object> reconcileAll() {
        Map<String, Object> result = new HashMap<>();
        
        Set<String> keySet = redisTemplate.keys(INIT_STOCK_KEY + "*");
        if (keySet == null || keySet.isEmpty()) {
            result.put("status", "OK");
            result.put("totalChecked", 0);
            return result;
        }
        
        int totalChecked = 0;
        int totalCorrected = 0;
        
        for (String key : keySet) {
            String skuIdStr = key.replace(INIT_STOCK_KEY, "");
            try {
                Long skuId = Long.parseLong(skuIdStr);
                Map<String, Object> skuResult = reconcile(skuId);
                totalChecked++;
                
                Boolean corrected = (Boolean) skuResult.get("corrected");
                if (corrected != null && corrected) {
                    totalCorrected++;
                }
            } catch (NumberFormatException e) {
                log.warn("[跳过无效key] {}", key);
            }
        }
        
        result.put("totalChecked", totalChecked);
        result.put("totalCorrected", totalCorrected);
        result.put("status", "OK");
        log.info("[全量对账完成] 共检查{}个SKU, 校正{}个", totalChecked, totalCorrected);
        
        return result;
    }

    /**
     * 查看库存状态（不对账）
     */
    public Map<String, Object> checkStock(Long skuId) {
        Map<String, Object> result = new HashMap<>();

        String redisStockStr = redisTemplate.opsForValue().get(GlobalConstants.SKU_STOCK_KEY + skuId);
        int redisStock = redisStockStr == null ? 0 : Integer.parseInt(redisStockStr);

        int totalOrders = orderRepository.countBySkuId(skuId);
        int paidOrders = orderRepository.countBySkuIdAndStatus(skuId, "SUCCESS");

        String initStockStr = redisTemplate.opsForValue().get(INIT_STOCK_KEY + skuId);
        int initialStock = initStockStr == null ? 0 : Integer.parseInt(initStockStr);

        result.put("skuId", skuId);
        result.put("initialStock", initialStock);
        result.put("redisStock", redisStock);
        result.put("totalOrders", totalOrders);
        result.put("paidOrders", paidOrders);
        result.put("expectedStock", initialStock - paidOrders);

        return result;
    }
}