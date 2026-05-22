package com.example.seckill;

import com.example.seckill.entity.Product;
import com.example.seckill.entity.ProductSku;
import com.example.seckill.repository.ProductRepository;
import com.example.seckill.repository.ProductSkuRepository;
import com.example.seckill.service.ComplexInventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 复杂商品+库存系统测试
 * 
 * 场景：5种水果参加秒杀
 * - 苹果（山东/陕西） 2个SKU
 * - 橙子（江西/湖北） 2个SKU  
 * - 香蕉（海南）      1个SKU
 * - 葡萄（新疆）      1个SKU
 * - 西瓜（宁夏）      1个SKU
 * 共计：7个SKU
 */
@SpringBootTest
public class ComplexInventoryTest {

    @Autowired
    private ComplexInventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSkuRepository productSkuRepository;

    private static final Long FRUIT_BOX_PRODUCT_ID = 1L;

    /**
     * 初始化测试数据：水果礼盒（5种水果7个SKU）
     */
    @BeforeEach
    public void initData() {
        // 清理旧数据
        productSkuRepository.deleteAll();
        productRepository.deleteAll();

        // 1. 创建SPU：水果礼盒
        Product fruitBox = new Product();
        fruitBox.setId(FRUIT_BOX_PRODUCT_ID);
        fruitBox.setName("水果礼盒");
        fruitBox.setCategory("水果");
        fruitBox.setSeckillEnabled(true);
        fruitBox.setSeckillStartTime(LocalDateTime.now().minusHours(1));
        fruitBox.setSeckillEndTime(LocalDateTime.now().plusHours(2));
        fruitBox.setCreatedAt(LocalDateTime.now());
        productRepository.save(fruitBox);

        // 2. 创建7个SKU
        createSku(1L, FRUIT_BOX_PRODUCT_ID, "山东红富士苹果", "12个装", "山东", new BigDecimal("88"), new BigDecimal("49"), 100);
        createSku(2L, FRUIT_BOX_PRODUCT_ID, "山东红富士苹果", "24个装", "山东", new BigDecimal("158"), new BigDecimal("89"), 50);
        createSku(3L, FRUIT_BOX_PRODUCT_ID, "江西赣南脐橙", "12个装", "江西", new BigDecimal("68"), new BigDecimal("39"), 80);
        createSku(4L, FRUIT_BOX_PRODUCT_ID, "湖北秭归脐橙", "24个装", "湖北", new BigDecimal("128"), new BigDecimal("69"), 60);
        createSku(5L, FRUIT_BOX_PRODUCT_ID, "海南香蕉", "5斤装", "海南", new BigDecimal("35"), new BigDecimal("19"), 120);
        createSku(6L, FRUIT_BOX_PRODUCT_ID, "新疆葡萄", "5斤装", "新疆", new BigDecimal("98"), new BigDecimal("59"), 40);
        createSku(7L, FRUIT_BOX_PRODUCT_ID, "宁夏西瓜", "1个装", "宁夏", new BigDecimal("58"), new BigDecimal("29"), 50);

        // 3. 预热到Redis
        inventoryService.preloadStock(FRUIT_BOX_PRODUCT_ID);

        System.out.println("========================================");
        System.out.println("【初始化】水果礼盒 - 7个SKU，总库存500");
    }

    /**
     * 辅助方法：创建SKU
     */
    private void createSku(Long id, Long productId, String skuName, String spec, 
                        String origin, BigDecimal price, BigDecimal seckillPrice, int stock) {
        ProductSku sku = new ProductSku();
        sku.setId(id);
        sku.setProductId(productId);
        sku.setSkuName(skuName);
        sku.setSpec(spec);
        sku.setOrigin(origin);
        sku.setPrice(price);
        sku.setSeckillPrice(seckillPrice);
        sku.setStock(stock);
        sku.setReservedStock(0);  // 预留库存，可设为正数用于其他渠道
        sku.setLockedStock(0);
        sku.setStatus(1);
        sku.setCreatedAt(LocalDateTime.now());
        productSkuRepository.save(sku);
    }

    /**
     * 测试一：查看所有商品和库存
     */
    @Test
    public void testListProducts() {
        System.out.println("\n===== 测试一：商品列表 =====");
        
        List<ProductSku> products = inventoryService.listProductsWithStock();
        
        System.out.println("----------------------------------------");
        for (ProductSku sku : products) {
            System.out.printf("【%s】%s %s | 库存=%d%n", 
                sku.getOrigin(), sku.getSkuName(), sku.getSpec(), sku.getSeckillStock());
        }
        System.out.println("========================================");
        
        assertFalse(products.isEmpty());
    }

    /**
     * 测试二：按商品秒杀（整个水果礼盒）
     */
    @Test
    public void testSeckillByProduct() {
        System.out.println("\n===== 测试二：按商品秒杀 =====");
        
        // 一个人的礼盒，包含所有7个SKU
        com.example.seckill.dto.SeckillRequest request = 
            new com.example.seckill.dto.SeckillRequest();
        request.setUserId(1001L);
        request.setProductId(FRUIT_BOX_PRODUCT_ID);
        request.setQuantity(1);
        
        var response = inventoryService.seckillByProduct(request);
        
        System.out.println("----------------------------------------");
        System.out.println("【请求】用户" + request.getUserId() + " 秒杀水果礼盒");
        System.out.println("【响应】code=" + response.getCode() + ", msg=" + response.getMessage());
        System.out.println("【数据】" + response.getData());
        System.out.println("========================================");
        
        assertEquals(200, response.getCode());
    }

    /**
     * 测试三：按单个SKU秒杀
     */
    @Test
    public void testSeckillBySku() {
        System.out.println("\n===== 测试三：单个SKU秒杀 =====");
        
        // 只秒杀山东苹果
        com.example.seckill.dto.SeckillRequest request = 
            new com.example.seckill.dto.SeckillRequest();
        request.setUserId(1002L);
        request.setSkuId(1L);  // 山东红富士苹果
        request.setQuantity(1);
        
        var response = inventoryService.seckillBySku(request);
        
        System.out.println("----------------------------------------");
        System.out.println("【请求】用户" + request.getUserId() + " 秒杀SKU=" + request.getSkuId());
        System.out.println("【响应】code=" + response.getCode() + ", msg=" + response.getMessage());
        System.out.println("========================================");
        
        assertEquals(200, response.getCode());
    }

    /**
     * 测试四：批量SKU秒杀（自由搭配）
     */
    @Test
    public void testSeckillBatch() {
        System.out.println("\n===== 测试四：批量SKU秒杀 =====");
        
        // 自由搭配：苹果+橙子+葡萄
        com.example.seckill.dto.SeckillRequest request = 
            new com.example.seckill.dto.SeckillRequest();
        request.setUserId(1003L);
        request.setSkuIds(List.of(1L, 3L, 6L));  // 苹果、橙子、葡萄
        
        var response = inventoryService.seckillBatch(request);
        
        System.out.println("----------------------------------------");
        System.out.println("【请求】用户" + request.getUserId() + " 秒杀" + request.getSkuIds());
        System.out.println("【响应】code=" + response.getCode() + ", msg=" + response.getMessage());
        System.out.println("【数据】" + response.getData());
        System.out.println("========================================");
        
        assertEquals(200, response.getCode());
    }

    /**
     * 测试五：查库存（不扣减）
     */
    @Test
    public void testQueryStock() {
        System.out.println("\n===== 测试五：查询库存 =====");
        
        // 查询单个SKU
        Integer skuStock = inventoryService.getSkuStock(1L);
        System.out.println("----------------------------------------");
        System.out.println("【SKU=1】剩余库存: " + skuStock);
        
        // 查询整个商品
        Map<String, Object> productStock = inventoryService.getProductStock(FRUIT_BOX_PRODUCT_ID);
        System.out.println("【商品】" + productStock);
        
        System.out.println("========================================");
        
        assertNotNull(skuStock);
    }

    /**
     * 测试六：并发秒杀多SKU
     */
    @Test
    public void testConcurrentMultiSku() throws InterruptedException {
        System.out.println("\n===== 测试六：并发测试 =====");
        
        int threads = 30;
        var latch = new java.util.concurrent.CountDownLatch(threads);
        int[] success = {0};
        
        for (int i = 0; i < threads; i++) {
            final long userId = 2000L + i;
            new Thread(() -> {
                try {
                    var request = new com.example.seckill.dto.SeckillRequest();
                    request.setUserId(userId);
                    request.setSkuId(5L);  // 海南香蕉，库存120
                    
                    var response = inventoryService.seckillBySku(request);
                    if (response.getCode() == 200) {
                        synchronized (success) { success[0]++; }
                        System.out.println("【成功】用户" + userId);
                    }
                } catch (Exception e) {
                    System.out.println("【异常】" + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        latch.await();
        
        Integer remaining = inventoryService.getSkuStock(5L);
        System.out.println("----------------------------------------");
        System.out.println("【统计】总请求=" + threads + ", 成功=" + success[0] + ", 剩余=" + remaining);
        System.out.println("========================================");
    }
}