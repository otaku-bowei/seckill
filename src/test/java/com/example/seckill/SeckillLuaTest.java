package com.example.seckill;

import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import com.example.seckill.service.SeckillServiceLua;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lua 脚本版本压测
 */
@SpringBootTest
public class SeckillLuaTest {

    @Autowired
    private SeckillServiceLua seckillService;

    @Test
    public void testInitStock() {
        seckillService.initStock(1L, 100);
        System.out.println("库存初始化完成");
    }

    @Test
    public void testSeckill() {
        SeckillRequest request = new SeckillRequest();
        request.setUserId(1001L);
        request.setSkuId(1L);
        request.setQuantity(1);

        SeckillResponse response = seckillService.seckill(request);
        System.out.println("结果: " + response);
    }

    @Test
    public void testDuplicateBuy() {
        for (int i = 0; i < 3; i++) {
            SeckillRequest request = new SeckillRequest();
            request.setUserId(1002L);
            request.setSkuId(1L);
            request.setQuantity(1);

            SeckillResponse response = seckillService.seckill(request);
            System.out.println("用户1002 第" + (i + 1) + "次: " + response);
        }
    }

    @Test
    public void testConcurrent() {
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long userId = 2000L + i;
            new Thread(() -> {
                try {
                    SeckillRequest request = new SeckillRequest();
                    request.setUserId(userId);
                    request.setSkuId(1L);
                    request.setQuantity(1);

                    SeckillResponse response = seckillService.seckill(request);
                    if (response.getCode() == 200) {
                        success.incrementAndGet();
                    } else {
                        fail.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("成功: " + success.get() + ", 失败: " + fail.get());
    }
}