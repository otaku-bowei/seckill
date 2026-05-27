# 秒杀系统

支持 **100,000 QPS** 的高性能秒杀系统，当前版本为基础实现，具备以下能力。

## 当前架构

```
用户请求 → Redis 库存扣减 → DB 订单落库
```

## 现有问题（百万级需升级）

| 问题 | 现状 | 百万级要求 |
|------|------|-----------|
| 并发锁 | Redisson 分布式锁（串行） | Lua 原子脚本 |
| 订单落库 | 直落 DB（同步阻塞） | MQ 异步 + 批量落库 |
| 限流 | 无 | Sentinel / 令牌桶 |
| 热点缓存 | 无 | 本地 Caffeine |

## 现有代码

### 1. 分布式锁扣库存

```java
// SeckillService.java
String lockKey = "seckill:lock:" + skuId;
RLock lock = redissonClient.getLock(lockKey);
lock.tryLock(3, 10, TimeUnit.SECONDS);  // 获取锁

String stockKey = "sku:stock:" + skuId;
Long stock = redisTemplate.opsForValue().decrement(stockKey, quantity);
if (stock < 0) {
    redisTemplate.opsForValue().increment(stockKey, quantity);
    return "商品已抢完";
}
```

### 2. 流程图

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   分布式锁   │───▶│ Redis扣减  │───▶│  订单落库  │
│  Redisson   │    │  库存判断  │    │  MySQL直落  │
└─────────────┘    └─────────────┘    └─────────────┘
```

## 升级方向（百万级）

### 1. Lua 原子扣库存（替代分布式锁）

```lua
-- stock.lua
local stockKey = KEYS[1]
local userKey = KEYS[2]
local quantity = tonumber(ARGV[1])

local stock = redis.call('GET', stockKey)
if not stock or tonumber(stock) < quantity then
    return -1  -- 库存不足
end
if redis.call('EXISTS', userKey) > 0 then
    return -2  -- 已抢购
end

redis.call('DECRBY', stockKey, quantity)
redis.call('SET', userKey, quantity, 'EX', 86400)
return 1  -- 成功
```

优势：无锁，O(1) 原子操作，吞吐量提升 10x+

### 2. 异步 MQ 削峰

```java
// 抢购成功后发送 MQ，不阻塞
rocketMQTemplate.convertAndSend("seckill-order", orderMessage);

// 消费者异步落库
@RocketMQMessageListener(topic = "seckill-order")
public class OrderConsumer {
    orderMapper.insert(order);  // 批量落库
}
```

### 3. 限流（Sentinel）

```java
@SentinelResource(value = "seckill", blockHandler = "blockHandler")
public Result seckill(SeckillRequest request) {
    // 限流规则
    // 每秒 10000 通过，其余拒绝
}
```

### 4. 本地热点缓存

```java
@Cacheable(value = "hotSku", key = "#skuId")
public SkuInfo getSkuInfo(Long skuId) {
    return redisTemplate.opsForValue().get("sku:" + skuId);
}
```

## 完整升级架构

```
┌─────────────────────────────────────────────────────────┐
│                      CDN / Nginx                       │
│                  静态资源 + IP 限流                    │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│                     API Gateway                       │
│              限流 + 路由 + 签名校验                    │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│              Spring Boot 集群 (多实例)                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │  本地缓存   │  │  本地缓存   │  │  本地缓存   │ │
│  │ Caffeine   │  │ Caffeine   │  │ Caffeine   │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        ┌──────────┐   ┌──────────┐   ┌──────────┐
        │  Lua脚本  │   │  Lua脚本  │   │  Lua脚本  │
        │  库存扣减 │   │  库存扣减 │   │  库存扣减 │
        └──────────┘   └──────────┘   └──────────┘
              │               │               │
              └───────────────┼───────────────┘
                              ▼
┌─────────────────────────────────────────────────────────┐
│                    Redis Cluster                        │
│              库存预热 + Lua 原子操作                     │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│                   RocketMQ                            │
│              异步订单消息 → 批量落库                    │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│                 MySQL 集群 (分库分表)                 │
│              订单异步写入 + 最终一致性                  │
└─────────────────────────────────────────────────────────┘
```

## 关键配置

```yaml
server:
  tomcat:
    threads:
      max: 800          # 增大线程池
      min-spare: 200   # 最小空闲
    accept-count: 500  # 排队队列

spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 500  # 连接池
          max-idle: 100   # 最大空闲
          min-idle: 50    # 最小空闲
```

## 性能目标

| 指标 | 基础版 | 百万级版 |
|------|--------|----------|
| QPS | 1,000 | 100,000+ |
| 延迟 | 50ms | < 20ms |
| 库存准确性 | 100% | 100% |

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
| 429 | 请求过于频繁 |
| 500 | 系统错误 |

## 压测

```bash
# 使用 wrk 压测
wrk -t10 -c100 -d10s -p10 \
  http://localhost:8080/api/seckill \
  -H "Content-Type: application/json" \
  -d '{"userId": 123, "skuId": 1, "quantity": 1}'
```

## 待完成

- [ ] Lua 原子扣库存脚本
- [ ] RocketMQ 异步落库
- [ ] Sentinel 限流
- [ ] 本地热点缓存
- [ ] 批量落库优化