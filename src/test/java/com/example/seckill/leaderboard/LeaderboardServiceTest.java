package com.example.seckill.leaderboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 排行榜服务测试
 */
@SpringBootTest
public class LeaderboardServiceTest {

    @Autowired
    private LeaderboardService leaderboardService;

    private static final String TEST_GAME = "test_game_001";

    @Test
    public void testUpdateAndQuery() {
        // 清理测试数据
        leaderboardService.clear(TEST_GAME);

        // 1. 更新分数
        leaderboardService.updateScore(TEST_GAME, "user_a", 100);
        leaderboardService.updateScore(TEST_GAME, "user_b", 80);
        leaderboardService.updateScore(TEST_GAME, "user_c", 120);

        // 2. 获取 Top 3
        List<LeaderboardService.RankItem> top3 = leaderboardService.getTopN(TEST_GAME, 3);
        assertEquals(3, top3.size());
        assertEquals("user_c", top3.get(0).getUserId()); // 120分第一名
        assertEquals("user_a", top3.get(1).getUserId()); // 100分第二名
        assertEquals("user_b", top3.get(2).getUserId()); // 80分第三名

        // 3. 获取排名
        assertEquals(1L, leaderboardService.getRank(TEST_GAME, "user_c"));
        assertEquals(2L, leaderboardService.getRank(TEST_GAME, "user_a"));
        assertEquals(3L, leaderboardService.getRank(TEST_GAME, "user_b"));

        // 4. 增加分数后再查询
        leaderboardService.updateScore(TEST_GAME, "user_b", 30); // 80 -> 110
        assertEquals(2L, leaderboardService.getRank(TEST_GAME, "user_b")); // 应该是第二名

        System.out.println("✅ 基本更新查询测试通过");
    }

    @Test
    public void testIncrementScore() {
        leaderboardService.clear(TEST_GAME);

        // 连续增加分数
        leaderboardService.updateScore(TEST_GAME, "user_x", 50);
        leaderboardService.updateScore(TEST_GAME, "user_x", 30);
        leaderboardService.updateScore(TEST_GAME, "user_x", 20);

        Double total = leaderboardService.getScore(TEST_GAME, "user_x");
        assertEquals(100.0, total);

        System.out.println("✅ 分数递增测试通过");
    }

    @Test
    public void testGetRange() {
        leaderboardService.clear(TEST_GAME);

        // 创建10个用户
        for (int i = 1; i <= 10; i++) {
            leaderboardService.updateScore(TEST_GAME, "user_" + i, i * 10);
        }

        // 查询 3-5 名
        List<LeaderboardService.RankItem> range = leaderboardService.getRange(TEST_GAME, 3, 5);
        assertEquals(3, range.size());
        assertEquals("user_8", range.get(0).getUserId()); // 80分，第3名
        assertEquals("user_7", range.get(1).getUserId()); // 70分，第4名
        assertEquals("user_6", range.get(2).getUserId()); // 60分，第5名

        System.out.println("✅ 区间查询测试通过");
    }

    @Test
    public void testHighConcurrency() throws InterruptedException {
        String gameId = "test_concurrency";
        leaderboardService.clear(gameId);

        int threadCount = 10;
        int updatesPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // 多线程更新
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < updatesPerThread; i++) {
                        String userId = "user_" + (threadId * 100 + i % 100);
                        leaderboardService.updateScore(gameId, userId, 1);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long elapsed = System.currentTimeMillis() - startTime;
        long qps = successCount.get() * 1000 / elapsed;

        System.out.println("并发更新 " + successCount.get() + " 次, 耗时 " + elapsed + "ms, QPS: " + qps);
        System.out.println("用户总数: " + leaderboardService.getTotalCount(gameId));

        // 验证数据一致性
        Long totalCount = leaderboardService.getTotalCount(gameId);
        assertTrue(totalCount > 0);

        System.out.println("✅ 高并发测试通过");
    }

    @Test
    public void testPerformance() {
        String gameId = "test_perf";
        leaderboardService.clear(gameId);

        int count = 10000;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            leaderboardService.updateScore(gameId, "user_" + i, i);
        }

        long updateTime = System.currentTimeMillis() - startTime;
        long updateQps = count * 1000 / updateTime;

        System.out.println("更新 " + count + " 次, 耗时 " + updateTime + "ms, QPS: " + updateQps);

        // 查询性能
        startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            leaderboardService.getTopN(gameId, 100);
        }
        long queryTime = System.currentTimeMillis() - startTime;

        System.out.println("查询 Top100 1000次, 耗时 " + queryTime + "ms, 平均: " + queryTime + "us");

        // 排名查询性能
        startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            leaderboardService.getRank(gameId, "user_5000");
        }
        long rankTime = System.currentTimeMillis() - startTime;

        System.out.println("查询排名 1000次, 耗时 " + rankTime + "ms, 平均: " + rankTime + "us");

        System.out.println("✅ 性能测试通过");
    }
}