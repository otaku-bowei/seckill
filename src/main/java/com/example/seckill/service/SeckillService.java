package com.example.seckill.service;

import com.example.seckill.constant.GlobalConstants;
import com.example.seckill.constant.LogCode;
import com.example.seckill.constant.LogUtils;
import com.example.seckill.dto.OrderMessage;
import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class SeckillService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public SeckillResponse seckill(SeckillRequest request) {
        Long userId = request.getUserId();
        Long skuId = request.getSkuId();
        Integer quantity = request.getQuantity();

        LogUtils.log(LogCode.S001, userId, skuId);

        String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
        String userKey = GlobalConstants.USER_BUY_KEY + skuId + ":" + userId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                GlobalConstants.STOCK_DEDUCT_SCRIPT, Long.class);

        List<String> keys = Arrays.asList(stockKey, userKey);
        Long result = redisTemplate.execute(script, keys,
                String.valueOf(quantity),
                String.valueOf(userId),
                "1");

        if (result == null || result == -1) {
            LogUtils.log(LogCode.S004, userId);
            return new SeckillResponse(400, "您已抢购过");
        }

        if (result == -2) {
            LogUtils.log(LogCode.S003, skuId);
            return new SeckillResponse(402, "商品已抢完");
        }

        if (result == 1) {
            // 生成订单号
            String orderNo = generateOrderNo(userId, skuId);
            
            // 构建订单消息
            OrderMessage orderMessage = OrderMessage.builder()
                    .orderNo(orderNo)
                    .userId(userId)
                    .skuId(skuId)
                    .quantity(quantity)
                    .status("PENDING")
                    .timestamp(System.currentTimeMillis())
                    .build();

            // 发送 MQ 消息（异步落库）
            try {
                rocketMQTemplate.convertAndSend("seckill-order", orderMessage);
                LogUtils.log(LogCode.S005, orderNo);
            } catch (Exception e) {
                System.out.println("MQ 发送失败: " + e.getMessage());
            }

            LogUtils.log(LogCode.S002, userId);
            return new SeckillResponse(200, "抢购成功", orderNo);
        }

        return new SeckillResponse(500, "系统错误");
    }

    public void initStock(Long skuId, Integer stock) {
        String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
    }

    public void createActivity(Long skuId) {
        String activityKey = GlobalConstants.ACTIVITY_KEY + skuId;
        redisTemplate.opsForValue().set(activityKey, "1");
    }

    private String generateOrderNo(Long userId, Long skuId) {
        return String.format("%d%d-%d", userId, skuId, System.currentTimeMillis());
    }
}