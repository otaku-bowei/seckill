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

/**
 * 秒杀服务
 * 
 * 按照Alibaba规范拆分为以下职责：
 * 1. 库存扣减 - deductStock()
 * 2. 结果处理 - handleDeductResult()
 * 3. 订单生成 - createOrderMessage()
 * 4. 消息发送 - sendOrderMessage()
 */
@Service
public class SeckillService {

    /** Redis操作 */
    @Autowired
    private StringRedisTemplate redisTemplate;

    /** MQ模板 */
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 抢购入口
     * 仅负责流程编排，各职责委托给私有方法
     */
    public SeckillResponse seckill(SeckillRequest request) {
        // 1. 参数校验与日志
        Long userId = request.getUserId();
        Long skuId = request.getSkuId();
        Integer quantity = request.getQuantity();
        
        LogUtils.log(LogCode.S001, userId, skuId);

        // 2. 执行库存扣减
        Long deductResult = deductStock(skuId, userId, quantity);
        
        // 3. 根据扣减结果处理
        return handleDeductResult(deductResult, userId, skuId, quantity);
    }

    /**
     * 库存扣减
     * 职责：执行Lua脚本，扣减Redis库存
     */
    private Long deductStock(Long skuId, Long userId, Integer quantity) {
        String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
        String userKey = GlobalConstants.USER_BUY_KEY + skuId + ":" + userId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                GlobalConstants.STOCK_DEDUCT_SCRIPT, Long.class);

        List<String> keys = Arrays.asList(stockKey, userKey);
        
        return redisTemplate.execute(script, keys,
                String.valueOf(quantity),
                String.valueOf(userId),
                "1");
    }

    /**
     * 处理库存扣减结果
     * 职责：根据扣减返回值，构建响应
     */
    private SeckillResponse handleDeductResult(Long result, Long userId, Long skuId, Integer quantity) {
        // 库存不足
        if (result == null || result == -2) {
            LogUtils.log(LogCode.S003, skuId);
            return new SeckillResponse(402, "商品已抢完");
        }

        // 已抢购过
        if (result == -1) {
            LogUtils.log(LogCode.S004, userId);
            return new SeckillResponse(400, "您已抢购过");
        }

        // 扣减成功，创建订单
        if (result == 1) {
            return createAndSendOrder(userId, skuId, quantity);
        }

        return new SeckillResponse(500, "系统错误");
    }

    /**
     * 创建并发送订单消息
     * 职责：生成订单号 -> 构建消息 -> 发送MQ
     */
    private SeckillResponse createAndSendOrder(Long userId, Long skuId, Integer quantity) {
        // 1. 生成订单号
        String orderNo = generateOrderNo(userId, skuId);
        
        // 2. 构建订单消息
        OrderMessage orderMessage = buildOrderMessage(orderNo, userId, skuId, quantity);
        
        // 3. 发送MQ消息
        sendOrderMessage(orderMessage);
        
        LogUtils.log(LogCode.S002, userId);
        return new SeckillResponse(200, "抢购成功", orderNo);
    }

    /**
     * 构建订单消息
     * 职责：封装订单数据
     */
    private OrderMessage buildOrderMessage(String orderNo, Long userId, Long skuId, Integer quantity) {
        return OrderMessage.builder()
                .orderNo(orderNo)
                .userId(userId)
                .skuId(skuId)
                .quantity(quantity)
                .status("PENDING")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 发送订单消息
     * 职责：将订单消息发送到RocketMQ
     */
    private void sendOrderMessage(OrderMessage orderMessage) {
        try {
            rocketMQTemplate.convertAndSend("seckill-order", orderMessage);
            LogUtils.log(LogCode.S005, orderMessage.getOrderNo());
        } catch (Exception e) {
            System.out.println("MQ发送失败: " + e.getMessage());
        }
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo(Long userId, Long skuId) {
        return String.format("%d%d-%d", userId, skuId, System.currentTimeMillis());
    }

    /**
     * 初始化库存（供测试/管理接口调用）
     */
    public void initStock(Long skuId, Integer stock) {
        String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
    }

    /**
     * 创建活动（启用秒杀活动）
     */
    public void createActivity(Long skuId) {
        String activityKey = GlobalConstants.ACTIVITY_KEY + skuId;
        redisTemplate.opsForValue().set(activityKey, "1");
    }
}