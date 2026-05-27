package com.example.seckill.leaderboard;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 排行榜控制器
 */
@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * 更新用户分数
     * POST /api/leaderboard/{gameId}/score
     * Body: { "userId": "user_123", "score": 50 }
     */
    @PostMapping("/{gameId}/score")
    public ResponseEntity<?> updateScore(
            @PathVariable String gameId,
            @RequestBody ScoreRequest request) {
        
        Double newScore = leaderboardService.updateScore(gameId, request.getUserId(), request.getScore());
        return ResponseEntity.ok(Map.of(
                "userId", request.getUserId(),
                "newScore", newScore
        ));
    }

    /**
     * 获取 Top N
     * GET /api/leaderboard/{gameId}/top?n=100
     */
    @GetMapping("/{gameId}/top")
    public ResponseEntity<?> getTopN(
            @PathVariable String gameId,
            @RequestParam(defaultValue = "100") int n) {
        
        List<LeaderboardService.RankItem> topN = leaderboardService.getTopN(gameId, n);
        return ResponseEntity.ok(Map.of(
                "gameId", gameId,
                "total", leaderboardService.getTotalCount(gameId),
                "list", topN
        ));
    }

    /**
     * 获取用户排名
     * GET /api/leaderboard/{gameId}/rank/{userId}
     */
    @GetMapping("/{gameId}/rank/{userId}")
    public ResponseEntity<?> getRank(
            @PathVariable String gameId,
            @PathVariable String userId) {
        
        Long rank = leaderboardService.getRank(gameId, userId);
        Double score = leaderboardService.getScore(gameId, userId);
        
        if (rank == null) {
            return ResponseEntity.ok(Map.of(
                    "userId", userId,
                    "rank", "未上榜",
                    "score", 0
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "rank", rank,
                "score", score != null ? score.longValue() : 0L
        ));
    }

    /**
     * 获取指定区间的排名
     * GET /api/leaderboard/{gameId}/range?start=101&end=200
     */
    @GetMapping("/{gameId}/range")
    public ResponseEntity<?> getRange(
            @PathVariable String gameId,
            @RequestParam int start,
            @RequestParam int end) {
        
        List<LeaderboardService.RankItem> list = leaderboardService.getRange(gameId, start, end);
        return ResponseEntity.ok(Map.of(
                "gameId", gameId,
                "start", start,
                "end", end,
                "list", list
        ));
    }

    /**
     * 清空排行榜
     * DELETE /api/leaderboard/{gameId}
     */
    @DeleteMapping("/{gameId}")
    public ResponseEntity<?> clear(@PathVariable String gameId) {
        leaderboardService.clear(gameId);
        return ResponseEntity.ok(Map.of("message", "排行榜已清空"));
    }

    /**
     * 获取排行榜总数
     * GET /api/leaderboard/{gameId}/count
     */
    @GetMapping("/{gameId}/count")
    public ResponseEntity<?> getCount(@PathVariable String gameId) {
        return ResponseEntity.ok(Map.of(
                "gameId", gameId,
                "count", leaderboardService.getTotalCount(gameId)
        ));
    }

    /**
     * 请求体
     */
    public static class ScoreRequest {
        private String userId;
        private double score;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
    }
}