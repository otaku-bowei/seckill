package com.example.seckill.leaderboard;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 排行榜服务 - 基于 Redis Sorted Set
 */
@Service
public class LeaderboardService {

    private final StringRedisTemplate redisTemplate;

    private static final String LEADERBOARD_PREFIX = "leaderboard:";

    public LeaderboardService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 增加分数（原子递增）
     *
     * @param gameId  游戏ID
     * @param userId  用户ID
     * @param score  增加的分数
     * @return 新的总分
     */
    public Double updateScore(String gameId, String userId, double score) {
        String key = buildKey(gameId);
        Double newScore = redisTemplate.opsForZSet().incrementScore(key, userId, score);
        return newScore != null ? newScore : 0.0;
    }

    /**
     * 设置分数
     */
    public Boolean setScore(String gameId, String userId, double score) {
        String key = buildKey(gameId);
        return redisTemplate.opsForZSet().add(key, userId, score);
    }

    /**
     * 获取用户排名（从1开始）
     *
     * @param gameId 游戏ID
     * @param userId  用户ID
     * @return 排名，不存在则返回null
     */
    public Long getRank(String gameId, String userId) {
        String key = buildKey(gameId);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId);
        return rank != null ? rank + 1 : null;
    }

    /**
     * 获取用户分数
     */
    public Double getScore(String gameId, String userId) {
        String key = buildKey(gameId);
        return redisTemplate.opsForZSet().score(key, userId);
    }

    /**
     * 获取 Top N
     *
     * @param gameId 游戏ID
     * @param n      前N名
     * @return 排行榜列表
     */
    public List<RankItem> getTopN(String gameId, int n) {
        String key = buildKey(gameId);
        Set<ZSetOperations.TypedTuple<String>> results = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, n - 1);

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        List<RankItem> list = new ArrayList<>();
        long rank = 1;
        for (var tuple : results) {
            list.add(new RankItem(
                    rank++,
                    tuple.getValue(),
                    tuple.getScore() != null ? tuple.getScore().longValue() : 0L
            ));
        }
        return list;
    }

    /**
     * 批量获取多个用户排名
     */
    public Map<String, Long> getRanks(String gameId, List<String> userIds) {
        String key = buildKey(gameId);
        Map<String, Long> result = new HashMap<>();

        for (String userId : userIds) {
            Long rank = redisTemplate.opsForZSet().reverseRank(key, userId);
            result.put(userId, rank != null ? rank + 1 : null);
        }
        return result;
    }

    /**
     * 获取用户总数
     */
    public Long getTotalCount(String gameId) {
        String key = buildKey(gameId);
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0L;
    }

    /**
     * 删除用户
     */
    public Long removeUser(String gameId, String userId) {
        String key = buildKey(gameId);
        return redisTemplate.opsForZSet().remove(key, userId);
    }

    /**
     * 清空排行榜
     */
    public Boolean clear(String gameId) {
        String key = buildKey(gameId);
        return redisTemplate.delete(key);
    }

    /**
     * 判断用户是否存在
     */
    public Boolean hasUser(String gameId, String userId) {
        String key = buildKey(gameId);
        return redisTemplate.opsForZSet().score(key, userId) != null;
    }

    /**
     * 获取指定区间的排名
     *
     * @param gameId 游戏ID
     * @param start 开始位置（从1开始）
     * @param end   结束位置（从1开始）
     */
    public List<RankItem> getRange(String gameId, int start, int end) {
        String key = buildKey(gameId);
        Set<ZSetOperations.TypedTuple<String>> results = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, start - 1, end - 1);

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        List<RankItem> list = new ArrayList<>();
        long rank = start;
        for (var tuple : results) {
            list.add(new RankItem(
                    rank++,
                    tuple.getValue(),
                    tuple.getScore() != null ? tuple.getScore().longValue() : 0L
            ));
        }
        return list;
    }

    private String buildKey(String gameId) {
        return LEADERBOARD_PREFIX + gameId;
    }

    /**
     * 排名项
     */
    public static class RankItem {
        private long rank;
        private String userId;
        private Long score;
        private String userName;
        private String avatar;

        public RankItem(long rank, String userId, Long score) {
            this.rank = rank;
            this.userId = userId;
            this.score = score;
        }

        // Getters and Setters
        public long getRank() { return rank; }
        public void setRank(long rank) { this.rank = rank; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public Long getScore() { return score; }
        public void setScore(Long score) { this.score = score; }

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }

        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }
}