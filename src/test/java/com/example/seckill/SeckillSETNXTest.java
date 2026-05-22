package com.example.seckill;

import com.example.seckill.constant.GlobalConstants;
import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import com.example.seckill.service.SeckillServiceSETNX;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SETNX方案测试 - 对比Lua方案展示问题
 */
@SpringBootTest
public class SeckillSETNXTest {

    @Autowired
    private SeckillServiceSETNX seckillServiceSETNX;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Long TEST_SKU_ID = 888L;

    @BeforeEach
    public void init() {
        redisTemplate.delete(GlobalConstants.SKU_STOCK_KEY + TEST_SKU_ID);
        seckillServiceSETNX.initStock(TEST_SKU_ID, 5);
    }

    /**
     * 测试一：正常流程
     */
    @Test
    public void testNormalFlow() {
        SeckillRequest request = new SeckillRequest();
        request.setUserId(100L);
        request.setSkuId(TEST_SKU_ID);

        SeckillResponse response = seckillServiceSETNX.seckillBySETNX(request);

        assertEquals(200, response.getCode());
        assertNotNull(response.getOrderNo());
    }

    /**
     * 测试二：重复购买
     */
    @Test
    public void testRepeatBuy() {
        SeckillRequest request = new SeckillRequest();
        request.setUserId(101L);
        request.setSkuId(TEST_SKU_ID);

        // 第一次
        SeckillResponse r1 = seckillServiceSETNX.seckillBySETNX(request);
        assertEquals(200, r1.getCode());

        // 第二次
        SeckillResponse r2 = seckillServiceSETNX.seckillBySETNX(request);
        assertEquals(400, r2.getCode());
    }

    /**
     * 测试三：高并发暴露问题（非原子性）
     * 
     * SETNX在高并发下可能出现：
     * - 时间窗口导致竞态条件
     * - 可能出现超卖（库存扣减非原子）
     */
    @Test
    public void testConcurrentRaceCondition() throws InterruptedException {
        int initialStock = 10;
        int threads = 100;

        // 清空之前数据
        redisTemplate.delete(GlobalConstants.SKU_STOCK_KEY + TEST_SKU_ID);
        seckillServiceSETNX.initStock(TEST_SKU_ID, initialStock);

        // 并发抢购
        Thread[] threadArr = new Thread[threads];
        int[] successCount = {0};

        for (int i = 0; i < threads; i++) {
            final long userId = 300L + i;
            threadArr[i] = new Thread(() -> {
                SeckillRequest req = new SeckillRequest();
                req.setUserId(userId);
                req.setSkuId(TEST_SKU_ID);
                
                SeckillResponse resp = seckillServiceSETNX.seckillBySETNX(req);
                if (resp.getCode() == 200) {
                    synchronized (successCount) {
                        successCount[0]++;
                    }
                }
            });
        }

        // 同时启动
        for (Thread t : threadArr) {
            t.start();
        }
        for (Thread t : threadArr) {
            t.join();
        }

        // 查看结果
        String remaining = redisTemplate.opsForValue()
                .get(GlobalConstants.SKU_STOCK_KEY + TEST_SKU_ID);
        
        System.out.println("=== SETNX 高并发测试 ===");
        System.out.println("初始库存: " + initialStock);
        System.out.println("成功数: " + successCount[0]);
        System.out.println("实际剩余: " + remaining);
        
        // 注意：SETNX可能出现超卖！因为多步操作非原子
        // Lua方案不会超卖
    }
}