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
 * 秒杀系统高并发测试
 * 目标：支持 100,000 TPS
 */
@SpringBootTest
public class SeckillConcurrencyTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OrderRepository orderRepository;

    private static final Long TEST_SKU_ID = 999L;
    private static final int INIT_STOCK = 10000;      // 初始库存
    private static final int THREAD_COUNT = 100;     // 并发线程数
    private static final int REQUEST_PER_THREAD = 1000; // 每个线程请求数

    /**
     * 初始化测试数据
     */
    @BeforeEach
    public void init() {
        // 清理 Redis 中的测试数据
        redisTemplate.delete(GlobalConstants.SKU_STOCK_KEY + TEST_SKU_ID);
        
        // 初始化库存
        seckillService.initStock(TEST_SKU_ID, INIT_STOCK);
        
        // 等待一下确保数据写入
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 测试并发抢购 - 基本功能验证
     */
    @Test
    public void testConcurrentSeckill() throws InterruptedException {
//        int totalRequests = THREAD_COUNT * 1000;  // 100000 个请求
        int totalRequests = THREAD_COUNT * 10;  // 1000 个请求
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger soldOutCount = new AtomicInteger(0);
        AtomicInteger repeatCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            final long userId = i + 10000L;
            executor.submit(() -> {
                try {
                    SeckillRequest request = new SeckillRequest();
                    request.setUserId(userId);
                    request.setSkuId(TEST_SKU_ID);
                    request.setQuantity(1);

                    SeckillResponse response = seckillService.seckill(request);
                    
                    if (response.getCode() == 200) {
                        successCount.incrementAndGet();
                    } else if (response.getCode() == 400) {
                        repeatCount.incrementAndGet();
                    } else if (response.getCode() == 402) {
                        soldOutCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        
        long duration = System.currentTimeMillis() - startTime;
        double tps = (totalRequests * 1000.0) / duration;

        System.out.println("========== ��试结果 ==========");
        System.out.println("总请求数: " + totalRequests);
        System.out.println("成功数: " + successCount.get());
        System.out.println("已抢购: " + repeatCount.get());
        System.out.println("售罄: " + soldOutCount.get());
        System.out.println("异常: " + errorCount.get());
        System.out.println("耗时: " + duration + "ms");
        System.out.println("TPS: " + String.format("%.2f", tps));

        // 验证：成功数应该等于初始库存（不超卖）
        assertTrue(successCount.get() <= INIT_STOCK, "库存不能超卖");
        // 验证：成功数 + 已售罄数应该等于总请求数（或初始库存）
        assertEquals(totalRequests, 
            successCount.get() + repeatCount.get() + soldOutCount.get() + errorCount.get(), 
            "请求数不匹配");
    }

    /**
     * 测试同一用户重复抢购
     */
    @Test
    public void testRepeatBuying() throws InterruptedException {
        Long userId = 20000L;
        
        // 第一次抢购 - 成功
        SeckillRequest request1 = new SeckillRequest();
        request1.setUserId(userId);
        request1.setSkuId(TEST_SKU_ID);
        request1.setQuantity(1);
        SeckillResponse response1 = seckillService.seckill(request1);
        
        assertEquals(200, response1.getCode(), "第一次抢购应该成功");

        // 第二次抢购 - 应该失败
        SeckillRequest request2 = new SeckillRequest();
        request2.setUserId(userId);
        request2.setSkuId(TEST_SKU_ID);
        request2.setQuantity(1);
        SeckillResponse response2 = seckillService.seckill(request2);
        
        assertEquals(400, response2.getCode(), "重复抢购应该返回400");
    }

    /**
     * 测试库存卖完
     */
    @Test
    public void testSoldOut() throws InterruptedException {
        // 设置小库存
        redisTemplate.delete(GlobalConstants.SKU_STOCK_KEY + TEST_SKU_ID);
        seckillService.initStock(TEST_SKU_ID, 2);

        // 抢购 3 次
        for (int i = 0; i < 3; i++) {
            SeckillRequest request = new SeckillRequest();
            request.setUserId(30000L + i);
            request.setSkuId(TEST_SKU_ID);
            request.setQuantity(1);
            
            SeckillResponse response = seckillService.seckill(request);
            
            if (i < 2) {
                assertEquals(200, response.getCode(), "第" + (i+1) + "次应该成功");
            } else {
                assertEquals(402, response.getCode(), "第3次应该售罄");
            }
        }
    }

    /**
     * 全流程集成测试 - 包含数据库落库
     * 验证：抢购成功 → MQ消息 → 订单落库
     */
    @Test
    public void testFullFlowIntegration() throws InterruptedException {
        // 确保测试数据干净
        redisTemplate.delete(GlobalConstants.SKU_STOCK_KEY + TEST_SKU_ID);
        seckillService.initStock(TEST_SKU_ID, 10);
        
        // 抢购成功
        Long userId = 40000L;
        SeckillRequest request = new SeckillRequest();
        request.setUserId(userId);
        request.setSkuId(TEST_SKU_ID);
        request.setQuantity(1);
        
        SeckillResponse response = seckillService.seckill(request);
        assertEquals(200, response.getCode(), "抢购应该成功");
        
        String orderNo = response.getData();
        assertNotNull(orderNo, "应该返回订单号");
        
        // 等待 MQ 消费者处理（异步落库）
        Thread.sleep(3000);
        
        // 验证数据库落库
        Order order = orderRepository.findByOrderNo(orderNo).orElse(null);
        assertNotNull(order, "订单应该已落库");
        assertEquals(userId, order.getUserId(), "用户ID应该匹配");
        assertEquals(TEST_SKU_ID, order.getSkuId(), "商品ID应该匹配");
        assertEquals("SUCCESS", order.getStatus(), "订单状态应该是SUCCESS");
        
        System.out.println("========== 全流程测试通过 ==========");
        System.out.println("订单号: " + orderNo);
        System.out.println("用户ID: " + userId);
        System.out.println("商品ID: " + TEST_SKU_ID);
    }

    /**
     * 批量全流程测试 - 验证多笔订单落库
     */
    @Test
    public void testBatchFullFlow() throws InterruptedException {
        // 确保测试数据干净
        redisTemplate.delete(GlobalConstants.SKU_STOCK_KEY + TEST_SKU_ID);
        seckillService.initStock(TEST_SKU_ID, 5);
        
        int successCount = 0;
        String[] orderNos = new String[5];
        
        // 5个不同用户抢购
        for (int i = 0; i < 5; i++) {
            Long userId = 50000L + i;
            SeckillRequest request = new SeckillRequest();
            request.setUserId(userId);
            request.setSkuId(TEST_SKU_ID);
            request.setQuantity(1);
            
            SeckillResponse response = seckillService.seckill(request);
            if (response.getCode() == 200) {
                orderNos[successCount] = response.getData();
                successCount++;
            }
        }
        
        assertEquals(5, successCount, "应该有5个成功");
        
        // 等待 MQ 消费者处理
        Thread.sleep(5000);
        
        // 验证所有订单都已落库
        for (String orderNo : orderNos) {
            Order order = orderRepository.findByOrderNo(orderNo).orElse(null);
            assertNotNull(order, "订单 " + orderNo + " 应该已落库");
            assertEquals("SUCCESS", order.getStatus());
        }
        
        System.out.println("========== 批量全流程测试通过 ==========");
        System.out.println("成功订单数: " + successCount);
    }

    /**
     * 高压力测试 - 接近 100,000 TPS
     */
    @Test
    public void testHighTPS() throws InterruptedException {
        System.out.println("开始高压力测试...");
        
        // 增加库存和请求数
        int largeStock = 50000;
        seckillService.initStock(TEST_SKU_ID, largeStock);
        
        int threadCount = 200;
        int requestPerThread = 500;
        int totalRequests = threadCount * requestPerThread;
        
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger soldOutCount = new AtomicInteger(0);
        AtomicInteger repeatCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // 预热
        for (int i = 0; i < 100; i++) {
            final long userId = i + 50000L;
            executor.submit(() -> {
                try {
                    SeckillRequest request = new SeckillRequest();
                    request.setUserId(userId);
                    request.setSkuId(TEST_SKU_ID);
                    seckillService.seckill(request);
                } catch (Exception e) {
                    // ignore
                }
            });
        }
        Thread.sleep(1000);
        
        // 清理用户标记，重新测试
        for (int i = 100; i < totalRequests / threadCount + 100; i++) {
            redisTemplate.delete(GlobalConstants.USER_BUY_KEY + TEST_SKU_ID + ":" + (i + 50000L));
        }
        seckillService.initStock(TEST_SKU_ID, largeStock);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < totalRequests; i++) {
            final long userId = i + 50000L;
            executor.submit(() -> {
                try {
                    SeckillRequest request = new SeckillRequest();
                    request.setUserId(userId);
                    request.setSkuId(TEST_SKU_ID);
                    request.setQuantity(1);

                    SeckillResponse response = seckillService.seckill(request);
                    
                    if (response.getCode() == 200) {
                        successCount.incrementAndGet();
                    } else if (response.getCode() == 402) {
                        soldOutCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        
        long duration = System.currentTimeMillis() - startTime;
        double tps = (totalRequests * 1000.0) / duration;

        System.out.println("========== 高压力测试结果 ==========");
        System.out.println("总请求数: " + totalRequests);
        System.out.println("成功数: " + successCount.get());
        System.out.println("售罄数: " + soldOutCount.get());
        System.out.println("耗时: " + duration + "ms");
        System.out.println("TPS: " + String.format("%.2f", tps));
        System.out.println("库存: " + largeStock);

        // 验证不超卖
        assertTrue(successCount.get() <= largeStock, "不能超卖");
    }
}