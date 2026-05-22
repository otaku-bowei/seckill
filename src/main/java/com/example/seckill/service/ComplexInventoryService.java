package com.example.seckill.service;

import com.example.seckill.constant.GlobalConstants;
import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.dto.SeckillResponse;
import com.example.seckill.entity.Product;
import com.example.seckill.entity.ProductSku;
import com.example.seckill.repository.ProductRepository;
import com.example.seckill.repository.ProductSkuRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 复杂商品+库存服务
 * 
 * 支持场景：
 * 1. 多SPU - 不同商品（苹果、橙子、香蕉等）
 * 2. 多SKU - 同商品不同规格（山东红富士12个装、24个装）
 * 3. 库存分层 - 总库存、预留库存、可秒杀库存
 * 4. 多维度查询 - 按商品、按SKU、按分类
 */
@Service
public class ComplexInventoryService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSkuRepository productSkuRepository;

    // ============================================================
    // 核心：多SKU秒杀库存扣减（基于商品维度）
    // ============================================================

    /**
     * 按商品下单（该商品所有SKU参与秒杀）
     * 
     * 例如：秒杀"水果礼盒"，包含5种水果
     * 一次扣减一个或多个SKU的库存
     */
    public SeckillResponse seckillByProduct(SeckillRequest request) {
        Long productId = request.getProductId();
        Long userId = request.getUserId();
        Integer quantity = request.getQuantity();

        // 1. 获取该商品所有SKU列表
        List<ProductSku> skuList = productSkuRepository.findByProductId(productId);
        if (skuList == null || skuList.isEmpty()) {
            return new SeckillResponse(404, "商品不存在");
        }

        // 2. 检查活动是否开启
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !product.getSeckillEnabled()) {
            return new SeckillResponse(403, "秒杀活动未开启");
        }

        // 3. 批量扣减所有SKU库存
        StringBuilder successSkus = new StringBuilder();
        int failCount = 0;

        for (ProductSku sku : skuList) {
            Long deductResult = deductSkuStock(sku.getId(), userId, quantity);
            if (deductResult == 1) {
                successSkus.append(sku.getSkuName()).append(",");
            } else {
                failCount++;
            }
        }

        // 4. 如果全部失败
        if (failCount == skuList.size()) {
            return new SeckillResponse(402, "所有商品已抢完");
        }

        // 5. 部分成功还是全成功
        if (failCount > 0) {
            return new SeckillResponse(207, "部分商品售罄", successSkus.toString());
        }

        return new SeckillResponse(200, "抢购成功", "product-" + productId);
    }

    /**
     * 按指定SKU下单（单一SKU）
     */
    public SeckillResponse seckillBySku(SeckillRequest request) {
        Long skuId = request.getSkuId();
        Long userId = request.getUserId();
        Integer quantity = request.getQuantity();

        // 1. 查询SKU信息
        ProductSku sku = productSkuRepository.findById(skuId).orElse(null);
        if (sku == null) {
            return new SeckillResponse(404, "SKU不存在");
        }

        // 2. 检查商品秒杀状态
        Product product = productRepository.findById(sku.getProductId()).orElse(null);
        if (product == null || !product.getSeckillEnabled()) {
            return new SeckillResponse(403, "秒杀活动未开启");
        }

        // 3. 扣减库存（Lua保证原子性）
        Long result = deductSkuStock(skuId, userId, quantity);

        if (result == -1) {
            return new SeckillResponse(400, "您已抢购过");
        }
        if (result == -2) {
            return new SeckillResponse(402, "商品已抢完");
        }
        if (result == 1) {
            return new SeckillResponse(200, "抢购成功", "sku-" + skuId);
        }

        return new SeckillResponse(500, "系统错误");
    }

    /**
     * 批量下单（多个SKU，每个买1件）
     * 
     * 例如：一个水果礼盒包含苹果+橙子+香蕉+葡萄+西瓜各1个
     */
    public SeckillResponse seckillBatch(SeckillRequest request) {
        List<Long> skuIds = request.getSkuIds();
        Long userId = request.getUserId();

        if (skuIds == null || skuIds.isEmpty()) {
            return new SeckillResponse(400, "请选择商品");
        }

        StringBuilder successMsg = new StringBuilder();
        StringBuilder failMsg = new StringBuilder();

        for (Long skuId : skuIds) {
            Long result = deductSkuStock(skuId, userId, 1);
            if (result == 1) {
                successMsg.append(skuId).append(",");
            } else {
                failMsg.append(skuId).append(",");
            }
        }

        if (successMsg.length() == 0) {
            return new SeckillResponse(402, "所有商品已抢完");
        }

        if (failMsg.length() > 0) {
            return new SeckillResponse(207, "部分商品售罄", successMsg.toString());
        }

        return new SeckillResponse(200, "抢购成功", successMsg.toString());
    }

    // ============================================================
    // 库存扣减核心（Lua脚本）
    // ============================================================

    /**
     * 单SKU库存扣减
     */
    private Long deductSkuStock(Long skuId, Long userId, Integer quantity) {
        String stockKey = GlobalConstants.SKU_STOCK_KEY + skuId;
        String userKey = GlobalConstants.USER_BUY_KEY + skuId + ":" + userId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                GlobalConstants.STOCK_DEDUCT_SCRIPT, Long.class);

        List<String> keys = Arrays.asList(stockKey, userKey);
        return redisTemplate.execute(script, keys,
                String.valueOf(quantity),
                String.valueOf(userId),
                "1");
    }

    // ============================================================
    // 库存查询（不扣减）
    // ============================================================

    /**
     * 查询商品库存（所有SKU汇总）
     */
    public Map<String, Object> getProductStock(Long productId) {
        List<ProductSku> skuList = productSkuRepository.findByProductId(productId);

        int totalStock = 0;
        int totalAvailable = 0;

        for (ProductSku sku : skuList) {
            // 从Redis查实时库存
            String stockStr = redisTemplate.opsForValue()
                    .get(GlobalConstants.SKU_STOCK_KEY + sku.getId());
            int redisStock = stockStr == null ? 0 : Integer.parseInt(stockStr);

            totalStock += sku.getStock();
            totalAvailable += redisStock;
        }

        return Map.of(
                "productId", productId,
                "totalStock", totalStock,
                "availableStock", totalAvailable
        );
    }

    /**
     * 查询单SKU库存
     */
    public Integer getSkuStock(Long skuId) {
        String stockStr = redisTemplate.opsForValue()
                .get(GlobalConstants.SKU_STOCK_KEY + skuId);
        return stockStr == null ? 0 : Integer.parseInt(stockStr);
    }

    /**
     * 查询所有商品（含库存）
     */
    public List<ProductSku> listProductsWithStock() {
        List<ProductSku> skuList = productSkuRepository.findAll();

        // 补充实时库存
        return skuList.stream().map(sku -> {
            Integer stock = getSkuStock(sku.getId());
            sku.setSeckillStock(stock);
            return sku;
        }).collect(Collectors.toList());
    }

    // ============================================================
    // 库存预热（Redis）
    // ============================================================

    /**
     * 将商品库存加载到Redis
     */
    public void preloadStock(Long productId) {
        List<ProductSku> skuList = productSkuRepository.findByProductId(productId);

        for (ProductSku sku : skuList) {
            // 可秒杀库存 = 总库存 - 预留库存
            int availableStock = sku.getStock() - sku.getReservedStock();
            redisTemplate.opsForValue().set(
                    GlobalConstants.SKU_STOCK_KEY + sku.getId(),
                    String.valueOf(availableStock)
            );
        }
    }
}