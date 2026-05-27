package com.example.seckill;

import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import com.example.seckill.service.SeckillService;
import com.example.seckill.service.SeckillServiceLua;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.seckill.constant.GlobalConstants.SKU_STOCK_KEY;

/**
 * 性能对比测试：Redisson 锁 vs Lua 脚本
 */
@SpringBootTest
@DisplayName("Redisson vs Lua 性能对比")
@ActiveProfiles("test")
public class PerformanceCompareTest {

    @Autowired
    private SeckillService seckillService;  // Redisson 版本

    @Autowired
    private SeckillServiceLua seckillServiceLua;  // Lua 版本

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    // 测试参数
    private static final int THREAD_COUNT = 100;      // 并发线程数
    private static final int REQUEST_PER_THREAD = 100; // 每个线程请求数
    private static final int STOCK = 10000;        // 初始库存

    @BeforeEach
    private void cleanup() {
        // 清理旧数据
        redisTemplate.delete(SKU_STOCK_KEY + 100L);
        redisTemplate.delete(SKU_STOCK_KEY + 200L);
    }

    /**
     * 初始化库存
     */
    private void initStock(long skuId, int stock) {
        redisTemplate.delete(SKU_STOCK_KEY + skuId);
        redisTemplate.opsForValue().set(SKU_STOCK_KEY + skuId, String.valueOf(stock));
    }

    /**
     * 测试 Redisson 版本
     */
    @Test
    @DisplayName("Redisson 分布式锁版本")
    public void testRedisson() throws InterruptedException {
        long skuId = 100L;
        initStock(skuId, STOCK);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);
        AtomicInteger totalTime = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < REQUEST_PER_THREAD; i++) {
                        long userId = 10000L + threadId * 1000 + i;
                        
                        long reqStart = System.nanoTime();
                        
                        SeckillRequest request = new SeckillRequest();
                        request.setUserId(userId);
                        request.setSkuId(skuId);
                        request.setQuantity(1);

                        SeckillResponse response = seckillService.seckill(request);
                        
                        int elapsed = (int) (System.nanoTime() - reqStart);
                        totalTime.addAndGet(elapsed);

                        if (response.getCode() == 200) {
                            success.incrementAndGet();
                        } else {
                            fail.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        long totalMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        int totalRequests = THREAD_COUNT * REQUEST_PER_THREAD;
        System.out.println("\n========== Redisson 版本 ==========");
        System.out.println("总请求数: " + totalRequests);
        System.out.println("成功: " + success.get());
        System.out.println("失败/售罄: " + fail.get());
        System.out.println("总耗时: " + totalMs + " ms");
        System.out.println("QPS: " + (totalRequests * 1000 / totalMs));
        System.out.println("平均响应时间: " + (totalTime.get() / totalRequests / 1000) + " us");
    }

    /**
     * 测试 Lua 版本
     */
    @Test
    @DisplayName("Lua 原子脚本版本")
    public void testLua() throws InterruptedException {
        long skuId = 200L;
        initStock(skuId, STOCK);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);
        AtomicInteger totalTime = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < REQUEST_PER_THREAD; i++) {
                        long userId = 20000L + threadId * 1000 + i;
                        
                        long reqStart = System.nanoTime();
                        
                        SeckillRequest request = new SeckillRequest();
                        request.setUserId(userId);
                        request.setSkuId(skuId);
                        request.setQuantity(1);

                        SeckillResponse response = seckillServiceLua.seckill(request);
                        
                        int elapsed = (int) (System.nanoTime() - reqStart);
                        totalTime.addAndGet(elapsed);

                        if (response.getCode() == 200) {
                            success.incrementAndGet();
                        } else {
                            fail.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        long totalMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        int totalRequests = THREAD_COUNT * REQUEST_PER_THREAD;
        System.out.println("\n========== Lua 版本 ==========");
        System.out.println("总请求数: " + totalRequests);
        System.out.println("成功: " + success.get());
        System.out.println("失败/售罄: " + fail.get());
        System.out.println("总耗时: " + totalMs + " ms");
        System.out.println("QPS: " + (totalRequests * 1000 / totalMs));
        System.out.println("平均响应时间: " + (totalTime.get() / totalRequests / 1000) + " us");
    }

    /**
     * 对比总结（顺序运行）
     */
    @Test
    @DisplayName("性能对比总结")
    public void testCompare() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("       性能对比：Redisson vs Lua        ");
        System.out.println("========================================");
        System.out.println("测试参数:");
        System.out.println("  - 并发线程数: " + THREAD_COUNT);
        System.out.println("  - 每线程请求数: " + REQUEST_PER_THREAD);
        System.out.println("  - 总请求数: " + (THREAD_COUNT * REQUEST_PER_THREAD));
        System.out.println("  - 初始库存: " + STOCK);
        System.out.println("建议：分别运行上面两个测试，对比输出��果");
        System.out.println("========================================");
    }
}