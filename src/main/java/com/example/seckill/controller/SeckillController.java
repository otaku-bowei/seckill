package com.example.seckill.controller;

import com.example.seckill.constant.LogUtils;
import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import com.example.seckill.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @PostMapping
    public ResponseEntity<SeckillResponse> seckill(@RequestBody SeckillRequest request) {
        SeckillResponse response = seckillService.seckill(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/init")
    public ResponseEntity<String> initStock(@RequestParam Long skuId, @RequestParam Integer stock) {
        seckillService.initStock(skuId, stock);
        seckillService.createActivity(skuId);
        return ResponseEntity.ok("OK");
    }
}