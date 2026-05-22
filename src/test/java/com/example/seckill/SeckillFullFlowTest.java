package com.example.seckill;

import com.example.seckill.constant.GlobalConstants;
import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import com.example.seckill.entity.Order;
import com.example.seckill.repository.OrderRepository;
import com.example.seckill.service.SeckillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 秒杀系统全流程测试
 * 目标：支持 100,000 TPS
 * 
 * 测试覆盖：
 * 1. 并发抢购
 * 2. 全流程落库
 * 3. 失败场景日志
 */
@SpringBootTest
public class SeckillFullFlowTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OrderRepository orderRepository;

    private static final Long TEST_SKU_ID = 999L;
    private static final int INIT_STOCK = 100;     // 小库存，方便看各种结果
    private static final int THREAD_COUNT = 50; // 并发线程

    /**
     * 初始化测试数据
     */
    @BeforeEach
    public void init() {
        // 清理 Redis 测试数据
        redisTemplate.delete(GlobalConstants.SKU_STOCK_KEY + TEST_SKU_ID);
        
        // 初始化库存
        seckillService.initStock(TEST_SKU_ID, INIT_STOCK);
        
        System.out.println("\n========================================");
        System.out.println("【测试初始化】SKU=" + TEST_SKU_ID + ", 库存=" + INIT_STOCK);
        
        try { Thread.sleep(100); } catch (InterruptedException e) {}
    }

    /**
     * 测试一：全流程测试 - 成功抢购 + 订单落库
     */
    @Test
    public void testSuccessFlowWithDB() throws InterruptedException {
        System.out.println("\n===== 测试一：成功抢购 + 订单落库 =====");
        
        Long userId = 101L;
        SeckillRequest request = new SeckillRequest();
        request.setUserId(userId);
        request.setSkuId(TEST_SKU_ID);
        request.setQuantity(1);
        
        // 1. 抢购
        SeckillResponse response = seckillService.seckill(request);
        
        // 2. 打印结果
        System.out.println("----------------------------------------");
        System.out.println("【请求】用户ID=" + userId + ", SKU=" + TEST_SKU_ID);
        System.out.println("【响应】code=" + response.getCode() + ", msg=" + response.getMessage());
        
        // 断言
        assertEquals(200, response.getCode(), "抢购应该成功");
        
        String orderNo = response.getData();
        assertNotNull(orderNo, "应该返回订单号");
        System.out.println("【订单号】" + orderNo);
        
        // 3. 等待MQ落库
        System.out.println("【等待】MQ消费者处理...");
        Thread.sleep(3000);
        
        // 4. 验证数据库
        Order order = orderRepository.findByOrderNo(orderNo).orElse(null);
        assertNotNull(order, "订单应该已落库");
        
        System.out.println("【落库】" + order);
        System.out.println("【状态】" + order.getStatus());
        System.out.println("========================================");
        
        assertEquals("SUCCESS", order.getStatus());
    }

    /**
     * 测试二：库存不足场景
     */
    @Test
    public void testSoldOut() throws InterruptedException {
        System.out.println("\n===== 测试二：库存不足场景 =====");
        
        // 重置为小库存
        redisTemplate.delete(GlobalConstants.SKU_STOCK_KEY + TEST_SKU_ID);
        seckillService.initStock(TEST_SKU_ID, 2);
        
        // 抢3次，最后一次应该失败
        for (int i = 0; i < 3; i++) {
            Long userId = 20001L + i;
            SeckillRequest request = new SeckillRequest();
            request.setUserId(userId);
            request.setSkuId(TEST_SKU_ID);
            
            SeckillResponse response = seckillService.seckill(request);
            
            System.out.println("----------------------------------------");
            System.out.println("【尝试】用户" + userId);
            System.out.println("【响应】code=" + response.getCode() + ", msg=" + response.getMessage());
            
            if (i < 2) {
                assertEquals(200, response.getCode());
                System.out.println("【结果】✓ 抢购成功，订单号=" + response.getData());
            } else {
                assertEquals(402, response.getCode());
                System.out.println("【结果】✗ 商品已抢完");
            }
        }
        
        // 看库存
        String stock = redisTemplate.opsForValue().get(GlobalConstants.SKU_STOCK_KEY + TEST_SKU_ID);
        System.out.println("----------------------------------------");
        System.out.println("【库存】剩余=" + stock);
        System.out.println("========================================");
    }

    /**
     * 测试三：重复购买场景
     */
    @Test
    public void testRepeatBuy() throws InterruptedException {
        System.out.println("\n===== 测试三：重复购买场景 =====");
        
        Long userId = 30001L;
        
        // 第一次抢购
        SeckillRequest request1 = new SeckillRequest();
        request1.setUserId(userId);
        request1.setSkuId(TEST_SKU_ID);
        
        SeckillResponse r1 = seckillService.seckill(request1);
        System.out.println("----------------------------------------");
        System.out.println("【第一次】用户" + userId);
        System.out.println("【响应】code=" + r1.getCode() + ", msg=" + r1.getMessage());
        
        assertEquals(200, r1.getCode());
        System.out.println("【结果】✓ 第一次抢购成功，订单号=" + r1.getData());
        
        // 第二次抢购
        SeckillRequest request2 = new SeckillRequest();
        request2.setUserId(userId);  // 同一个用户
        request2.setSkuId(TEST_SKU_ID);
        
        SeckillResponse r2 = seckillService.seckill(request2);
        System.out.println("----------------------------------------");
        System.out.println("【第二次】用户" + userId + "（同一人）");
        System.out.println("【响应】code=" + r2.getCode() + ", msg=" + r2.getMessage());
        
        assertEquals(400, r2.getCode());
        System.out.println("【结果】✗ 您已抢购过");
        
        System.out.println("========================================");
    }

    /**
     * 测试四：小规模并发测试 + 全流程日志
     */
    @Test
    public void testConcurrentWithLogs() throws InterruptedException {
        System.out.println("\n===== 测试四：并发测试（50人抢10个）=====");
        
        // 10个库存
        redisTemplate.delete(GlobalConstants.SKU_STOCK_KEY + TEST_SKU_ID);
        seckillService.initStock(TEST_SKU_ID, 10);
        
        int totalRequests = 50;
        CountDownLatch latch = new CountDownLatch(totalRequests);
        
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger soldOut = new AtomicInteger(0);
        AtomicInteger repeat = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        for (int i = 0; i < totalRequests; i++) {
            final long userId = 40001L + i;
            executor.submit(() -> {
                try {
                    SeckillRequest request = new SeckillRequest();
                    request.setUserId(userId);
                    request.setSkuId(TEST_SKU_ID);
                    
                    SeckillResponse response = seckillService.seckill(request);
                    
                    synchronized (SeckillFullFlowTest.class) {
                        if (response.getCode() == 200) {
                            System.out.println("【成功】用户" + userId + " → 订单" + response.getData());
                            success.incrementAndGet();
                        } else if (response.getCode() == 400) {
                            System.out.println("【重复】用户" + userId + " → " + response.getMessage());
                            repeat.incrementAndGet();
                        } else if (response.getCode() == 402) {
                            System.out.println("【售罄】用户" + userId + " → " + response.getMessage());
                            soldOut.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    System.out.println("【异常】" + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        System.out.println("----------------------------------------");
        System.out.println("【统计】总数=" + totalRequests);
        System.out.println("  - 成功: " + success.get());
        System.out.println("  - 重复: " + repeat.get());
        System.out.println("  - 售罄: " + soldOut.get());
        
        // 更新：刚才成功的10个应该落库了，现在查DB
        Thread.sleep(3000);
        long orderCount = orderRepository.count();
        System.out.println("【DB】数据库订单数=" + orderCount);
        
        System.out.println("========================================");
        
        assertTrue(success.get() <= 10, "成功数不应超过库存");
    }

    /**
     * 测试五：高压力测试（接近100000 TPS）
     */
    @Test
    public void testHighTPS() throws InterruptedException {
        System.out.println("\n===== 测试五：高压力测试 =====");
        
        int largeStock = 50000;
        seckillService.initStock(TEST_SKU_ID, largeStock);
        
        int threadCount = 200;
        int requestPerThread = 500;
        int totalRequests = threadCount * requestPerThread;
        
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger soldOut = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        System.out.println("【开始】" + totalRequests + " 并发请求");
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < totalRequests; i++) {
            final long userId = 50001L + i;
            executor.submit(() -> {
                try {
                    SeckillRequest request = new SeckillRequest();
                    request.setUserId(userId);
                    request.setSkuId(TEST_SKU_ID);
                    
                    SeckillResponse response = seckillService.seckill(request);
                    
                    if (response.getCode() == 200) {
                        success.incrementAndGet();
                    } else if (response.getCode() == 402) {
                        soldOut.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long duration = System.currentTimeMillis() - startTime;
        double tps = (totalRequests * 1000.0) / duration;
        
        System.out.println("----------------------------------------");
        System.out.println("【结果】");
        System.out.println("  - 总请求: " + totalRequests);
        System.out.println("  - 成功: " + success.get());
        System.out.println("  - 售罄: " + soldOut.get());
        System.out.println("  - 耗时: " + duration + "ms");
        System.out.println("  - TPS: " + String.format("%.2f", tps));
        System.out.println("========================================");
        
        assertTrue(success.get() <= largeStock);
    }
}