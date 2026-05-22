package com.example.seckill.service;

import com.example.seckill.constant.GlobalConstants;
import com.example.seckill.constant.LogCode;
import com.example.seckill.constant.LogUtils;
import com.example.seckill.dto.OrderMessage;
import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import com.example.seckill.entity.Order;
import com.example.seckill.repository.OrderRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀服务 - 直接落库，跳过MQ
 */
@Service
public class SeckillService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private OrderRepository orderRepository;

    private static final String LOCK_PREFIX = "seckill:lock:";
    private static final long WAIT_TIME = 3;
    private static final long LEASE_TIME = 10;
    private static final long USER_EXPIRE = 24 * 3600;

    public SeckillResponse seckill(SeckillRequest request) {
        Long userId = request.getUserId();
        Long skuId = request.getSkuId();
        Integer quantity = request.getQuantity();

        LogUtils.log(LogCode.S001, userId, skuId);

        String lockKey = LOCK_PREFIX + skuId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS);

            if (!acquired) {
                return new SeckillResponse(500, "系统繁忙，请稍后重试");
            }

            Long deductResult = deductStock(skuId, userId, quantity);
            return handleDeductResult(deductResult, userId, skuId, quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SeckillResponse(500, "系统错误");
        } catch (Exception e) {
            return new SeckillResponse(500, "系统错误: " + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Long deductStock(Long skuId, Long userId, Integer quantity) {
        String userKey = GlobalConstants.USER_BUY_KEY + skuId + ":" + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(userKey))) {
            return -1L;
        }

        String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
        Long stock = redisTemplate.opsForValue().decrement(stockKey, quantity);

        if (stock == null || stock < 0) {
            redisTemplate.opsForValue().increment(stockKey, quantity);
            return -2L;
        }

        redisTemplate.opsForValue().set(userKey, String.valueOf(quantity));
        redisTemplate.expire(userKey, USER_EXPIRE, TimeUnit.SECONDS);

        return 1L;
    }

    private SeckillResponse handleDeductResult(Long result, Long userId, Long skuId, Integer quantity) {
        if (result == -1L) {
            LogUtils.log(LogCode.S004, userId);
            return new SeckillResponse(400, "您已抢购过");
        }

        if (result == -2L) {
            LogUtils.log(LogCode.S003, skuId);
            return new SeckillResponse(402, "商品已抢完");
        }

        if (result == 1L) {
            return createAndSaveOrder(userId, skuId, quantity);
        }

        return new SeckillResponse(500, "系统错误");
    }

    private SeckillResponse createAndSaveOrder(Long userId, Long skuId, Integer quantity) {
        String orderNo = generateOrderNo(userId, skuId);

        // 直接保存到数据库
        Order order = Order.builder()
                .orderNo(orderNo)
                .userId(userId)
                .skuId(skuId)
                .quantity(quantity)
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();

        orderRepository.save(order);
        
        System.out.println("[订单落库成功] orderNo=" + orderNo + ", userId=" + userId + ", skuId=" + skuId);

        LogUtils.log(LogCode.S002, userId);
        return new SeckillResponse(200, "抢购成功", orderNo);
    }

    private String generateOrderNo(Long userId, Long skuId) {
        return String.format("%d%d-%d", userId, skuId, System.currentTimeMillis());
    }

    @Autowired
    private ReconcileService reconcileService;

    public void initStock(Long skuId, Integer stock) {
        // 记录初始库存（用于对账）
        reconcileService.recordInitialStock(skuId, stock);
        // 初始化可销售库存
        String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
    }

    public void createActivity(Long skuId) {
        String activityKey = GlobalConstants.ACTIVITY_KEY + skuId;
        redisTemplate.opsForValue().set(activityKey, "1");
    }
}