package com.example.seckill.service;

import com.example.seckill.constant.GlobalConstants;
import com.example.seckill.constant.LogCode;
import com.example.seckill.constant.LogUtils;
import com.example.seckill.dto.OrderMessage;
import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀服务 - 使用本地模式跳过MQ
 * 如果MQ连不上，订单会直接记录不发送
 */
@Service
public class SeckillService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    // 是否启用MQ（由于MQ认证问题，暂时禁用）
    private static final boolean MQ_ENABLED = false;

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
            return createAndSendOrder(userId, skuId, quantity);
        }

        return new SeckillResponse(500, "系统错误");
    }

    private SeckillResponse createAndSendOrder(Long userId, Long skuId, Integer quantity) {
        String orderNo = generateOrderNo(userId, skuId);

        OrderMessage orderMessage = OrderMessage.builder()
                .orderNo(orderNo)
                .userId(userId)
                .skuId(skuId)
                .quantity(quantity)
                .status("PENDING")
                .timestamp(System.currentTimeMillis())
                .build();

        // 记录订单（由于MQ认证问题，这里直接记录日志）
        saveOrder(orderMessage);

        LogUtils.log(LogCode.S002, userId);
        return new SeckillResponse(200, "抢购成功", orderNo);
    }

    private void saveOrder(OrderMessage orderMessage) {
        // 由于MQ认证暂时有问题，跳过MQ发送，直接记录到日志
        System.out.println("[订单已创建] orderNo=" + orderMessage.getOrderNo() + 
                       ", userId=" + orderMessage.getUserId() + 
                       ", skuId=" + orderMessage.getSkuId() +
                       ", status=" + orderMessage.getStatus());
        
        if (MQ_ENABLED) {
            // TODO: MQ认证配置好后启用
            // sendMQMessage(orderMessage);
        } else {
            System.out.println("[提示] MQ已禁用，订单仅记录到日志");
        }
    }

    private String generateOrderNo(Long userId, Long skuId) {
        return String.format("%d%d-%d", userId, skuId, System.currentTimeMillis());
    }

    public void initStock(Long skuId, Integer stock) {
        String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
    }

    public void createActivity(Long skuId) {
        String activityKey = GlobalConstants.ACTIVITY_KEY + skuId;
        redisTemplate.opsForValue().set(activityKey, "1");
    }
}