# 秒杀系统

支持 **100,000 TPS** 的高性能秒杀系统，基于 Redis + Lua 原子操作 + RocketMQ 异步落库实现。

## 技术架构

```
抢购请求 → Redis 库存扣减 → MQ 消息 → 订单落库
```

## 核心设计思路

### 1. 库存预热

```java
// 活动开始前，将库存加载到 Redis
@PostConstruct
public void warmUp() {
    Long stock = db.getStock(skuId);
    redis.set("sku:stock:" + skuId, stock);
}
```

### 2. Lua 原子操作（核心）

```lua
-- Lua 脚本保证原子性，避免并发问题
local stock = redis.call('GET', KEYS[1])
if not stock or tonumber(stock) < 1 then
    return -2  -- 库存不足
end
if redis.call('EXISTS', KEYS[2]) > 0 then
    return -1  -- 已抢购
end
redis.call('DECRBY', KEYS[1], 1)
redis.call('SET', KEYS[2], 1, 'EX', 86400)
return 1  -- 成功
```

### 3. RocketMQ 异步落库

```java
// 抢购成功，发送 MQ 消息
rocketMQTemplate.convertAndSend("seckill-order", orderMessage);

// 消费者监听并落库
@RocketMQMessageListener(topic = "seckill-order", consumerGroup = "seckill-consumer")
public class OrderConsumer implements RocketMQListener<OrderMessage> {
    orderRepository.save(order);  // 落库
}
```

### 4. 流程图

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   用户请求   │───▶│  Lua 扣减   │───▶│  MQ 消息   │───▶│  订单落库  │
│             │    │   库存     │    │   异步     │    │   MySQL    │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
```

```
┌─────────────────────────────────────────────────────────┐
│                      Load Balancer                      │
└─────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│   Gateway /     │ │   Gateway /     │ │   Gateway /     │
│   Nginx         │ │   Nginx         │ │   Nginx         │
└─────────────────┘ └─────────────────┘ └─────────────────┘
          │                   │                   │
          └───────────────────┼───────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────┐
│              Spring Boot 集群 (多实例)                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │  Instance 1  │  │  Instance 2  │  │  Instance N  │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│                    Redis 集群                          │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐   │
│  │ Master │──│ Master │──│ Master │──│ Master │   │
│  │ Node 1 │  │ Node 2 │  │ Node 3 │  │ Node N │   │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘   │
│       │            │            │                      │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐                │
│  │ Replica │  │ Replica │  │ Replica │                │
│  └─────────┘  └─────────┘  └─────────┘                │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌───────────────────────────��─────────────────────────────┐
│                mysql (异步落库)                         │
│         Order Service 异步写入订单                      │
└─────────────────────────────────────────────────────────┘
```

## 核心设计思路

### 1. 库存预热

```java
// 活动开始前，将库存加载到 Redis
// 缓存 miss 时从数据库加载
@PostConstruct
public void warmUp() {
    Long stock = db.getStock(skuId);
    redis.set("sku:stock:" + skuId, stock);
}
```

### 2. 内存标记 + 限流

```
                           ┌──────────────────┐
                           │   IP 限流 (每 IP  │
                           │   100 QPS)       │
                           └────────┬─────────┘
                                    │
                                    ▼
┌──────────────────┐      ┌──────────────────┐
│  活动未开始 (false)│─────▶│ 活动已开始 (true) │
│  直接返回         │      └────────┬─────────┘
└──────────────────┘               │
                                   ▼
                         ┌──────────────────┐
                         │   Lua 原子扣减   │
                         │   库存 - 1      │
                         │   用户标记      │
                         └────────┬─────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │   异步创建订单   │
                         │   到 MySQL        │
                         └──────────────────┘
```

### 3. Lua 原子操作（核心）

```lua
-- Lua 脚本保证原子性，避免并发问题
local stock = redis.call('GET', KEYS[1])
if not stock or tonumber(stock) < 1 then
    return -2  -- 库存不足
end
if redis.call('EXISTS', KEYS[2]) > 0 then
    return -1  -- 已抢购
end
redis.call('DECRBY', KEYS[1], 1)
redis.call('SET', KEYS[2], 1, 'EX', 86400)
return 1  -- 成功
```

### 4. 异步下单

```java
@Async
public Future<Order> createOrderAsync(SeckillRequest request) {
    // 异步写入订单，不阻塞主流程
    Order order = Order.builder()
        .userId(request.getUserId())
        .skuId(request.getSkuId())
        .status("PENDING")
        .build();
    return orderRepository.saveAsync(order);
}
```

### 5. 限流策略

| 层级 | 方式 | 说明 |
|------|------|------|
| **网关层** | Nginx 限流 | 每个 IP 100 QPS |
| **应用层** | Sentinel / RateLimiter | 接口限流 |
| **Redis 层** | Lua 脚本 | 库存原子扣减 |

## 高性能优化

### 关键配置

```yaml
server:
  tomcat:
    threads:
      max: 500          # 增大线程池
      min-spare: 100   # 最小空闲线程
    accept-count: 200  # 排队队列

spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 500  # 连接池
          max-idle: 100   # 最大空闲
          min-idle: 50    # 最小空闲
```

### 性能指标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| TPS | 100,000 | 每秒处理请求数 |
| P99 延迟 | < 50ms | 99% 请求响应时间 |
| 库存准确性 | 100% | 不超卖 |

## API

```bash
# 初始化库存
POST /api/seckill/init?skuId=1&stock=10000

# 抢购
POST /api/seckill
{
    "userId": 12345,
    "skuId": 1,
    "quantity": 1
}
```

## 响应码

| 状态码 | 说明 |
|--------|------|
| 200 | 抢购成功 |
| 400 | 已抢购过 |
| 402 | 商品已抢完 |
| 500 | 系统错误 |

## 压测

```bash
# 使用 wrk 压测
wrk -t10 -c100 -d10s -p10 \
  http://localhost:8080/api/seckill \
  -H "Content-Type: application/json" \
  -d '{"userId": 123, "skuId": 1, "quantity": 1}'
```

## 扩展思路

1. **多 SKU 限购** - Lua 脚本支持多商品
2. **排队机制** - 使用 Redis Stream 实现排队
3. **熔断降级** - Sentinel 熔断保护
4. **分库分表** - 用户 ID 哈希分表