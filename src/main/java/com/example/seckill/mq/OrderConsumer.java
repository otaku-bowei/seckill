package com.example.seckill.mq;

import com.example.seckill.constant.LogCode;
import com.example.seckill.constant.LogUtils;
import com.example.seckill.dto.OrderMessage;
import com.example.seckill.entity.Order;
import com.example.seckill.repository.OrderRepository;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单消息消费者
 * 监听 seckill-order 主题，异步落库
 */
@Component
@RocketMQMessageListener(
        topic = "seckill-order",
        consumerGroup = "seckill-consumer"
)
public class OrderConsumer implements RocketMQListener<OrderMessage> {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    @Autowired
    private OrderRepository orderRepository;

    @Override
    public void onMessage(OrderMessage message) {
        try {
            log.info("收到订单消息: {}", message.getOrderNo());

            // 构建订单实体
            Order order = Order.builder()
                    .orderNo(message.getOrderNo())
                    .userId(message.getUserId())
                    .skuId(message.getSkuId())
                    .quantity(message.getQuantity())
                    .status("SUCCESS")
                    .createdAt(LocalDateTime.now())
                    .build();

            // 落库
            orderRepository.save(order);
            
            LogUtils.log(LogCode.S005, message.getOrderNo());
            log.info("订单落库成功: {}", message.getOrderNo());

        } catch (Exception e) {
            log.error("订单落库失败: {}, error: {}", message.getOrderNo(), e.getMessage(), e);
        }
    }
}