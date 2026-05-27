package com.example.seckill.service;

import com.example.seckill.constant.GlobalConstants;
import com.example.seckill.constant.LogCode;
import com.example.seckill.constant.LogUtils;
import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import com.example.seckill.entity.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static com.example.seckill.constant.GlobalConstants.SKU_STOCK_KEY;
import static com.example.seckill.constant.GlobalConstants.SKU_USER_BUY_KEY;

/**
 * 秒杀服务 - Lua 原子脚本版本
 * 
 * 优势：
 * 1. 无锁，纯原子操作，并发能力提升 10x+
 * 2. 减少网络 RTT（无需锁的获取/释放）
 * 3. 单次 Redis 调用，无竞态条件
 */
@Service
public class SeckillServiceLua {

    private final StringRedisTemplate redisTemplate;

    private DefaultRedisScript<Long> stockDeductScript;

    public SeckillServiceLua(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        stockDeductScript = new DefaultRedisScript<>();
        stockDeductScript.setResultType(Long.class);
        stockDeductScript.setScriptSource(
            new ResourceScriptSource(new ClassPathResource("lua/stock_deduct.lua"))
        );
    }

    /**
     * 抢购入口
     */
    public SeckillResponse seckill(SeckillRequest request) {
        Long userId = request.getUserId();
        Long skuId = request.getSkuId();
        Integer quantity = request.getQuantity();

        LogUtils.log(LogCode.S001, userId, skuId);

        // 构建 key
        String stockKey = SKU_STOCK_KEY + skuId;
        String userKey = SKU_USER_BUY_KEY + skuId + ":" + userId;

        // 执行 Lua 脚本（原子操作）
        Long result = redisTemplate.execute(
            stockDeductScript,
            List.of(stockKey, userKey),
            String.valueOf(quantity)
        );

        return handleResult(result, userId, skuId, quantity);
    }

    private SeckillResponse handleResult(Long result, Long userId, Long skuId, Integer quantity) {
        switch (result != null ? result.intValue() : -999) {
            case 1:
                return createOrder(userId, skuId, quantity);
            case -1:
                LogUtils.log(LogCode.S005, skuId);
                return new SeckillResponse(500, "库存未初始化");
            case -2:
                LogUtils.log(LogCode.S003, skuId);
                return new SeckillResponse(402, "商品已抢完");
            case -3:
                LogUtils.log(LogCode.S004, userId);
                return new SeckillResponse(400, "您已抢购过");
            default:
                return new SeckillResponse(500, "系统错误");
        }
    }

    private SeckillResponse createOrder(Long userId, Long skuId, Integer quantity) {
        String orderNo = String.format("%d%d-%d", userId, skuId, System.currentTimeMillis());

        Order order = Order.builder()
                .orderNo(orderNo)
                .userId(userId)
                .skuId(skuId)
                .quantity(quantity)
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();

        // TODO: 改为 MQ 异步落库
        // orderRepository.save(order);

        System.out.println("[订单落库] orderNo=" + orderNo + ", userId=" + userId);
        
        LogUtils.log(LogCode.S002, userId);
        return new SeckillResponse(200, "抢购成功", orderNo);
    }

    /**
     * 初始化库存
     */
    public void initStock(Long skuId, Integer stock) {
        String stockKey = SKU_STOCK_KEY + skuId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
    }
}