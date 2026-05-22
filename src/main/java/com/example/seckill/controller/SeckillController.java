package com.example.seckill.controller;

import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import com.example.seckill.service.ReconcileService;
import com.example.seckill.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private ReconcileService reconcileService;

    /**
     * 抢购接口
     */
    @PostMapping
    public ResponseEntity<SeckillResponse> seckill(@RequestBody SeckillRequest request) {
        SeckillResponse response = seckillService.seckill(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * 初始化库存（秒杀开始前调用）
     * 会记录初始库存，用于后续对账
     */
    @PostMapping("/init")
    public ResponseEntity<String> initStock(@RequestParam Long skuId, @RequestParam Integer stock) {
        // 保存初始库存到Redis（用于对账）
        reconcileService.recordInitialStock(skuId, stock);
        // 初始化可销售库存
        seckillService.initStock(skuId, stock);
        seckillService.createActivity(skuId);
        return ResponseEntity.ok("OK");
    }

    /**
     * 对账单个SKU
     */
    @GetMapping("/reconcile/{skuId}")
    public ResponseEntity<Map<String, Object>> reconcile(@PathVariable Long skuId) {
        Map<String, Object> result = reconcileService.reconcile(skuId);
        return ResponseEntity.ok(result);
    }

    /**
     * 对账所有SKU
     */
    @GetMapping("/reconcileAll")
    public ResponseEntity<Map<String, Object>> reconcileAll() {
        Map<String, Object> result = reconcileService.reconcileAll();
        return ResponseEntity.ok(result);
    }

    /**
     * 查看库存状态（不对账）
     */
    @GetMapping("/check/{skuId}")
    public ResponseEntity<Map<String, Object>> checkStock(@PathVariable Long skuId) {
        Map<String, Object> result = reconcileService.checkStock(skuId);
        return ResponseEntity.ok(result);
    }
}