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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 随机商品库存测试
 * 每次运行随机生成不同数量的商品和SKU
 */
@SpringBootTest
public class RandomInventoryTest {

    @Autowired
    private ComplexInventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSkuRepository productSkuRepository;

    private static final Random random = new Random();
    private static final List<Long> createdProductIds = new ArrayList<>();

    /**
     * 随机初始化商品数据
     */
    @BeforeEach
    public void initRandomData() {
        // 清理
        productSkuRepository.deleteAll();
        productRepository.deleteAll();
        createdProductIds.clear();

        // 随机生成 1-3 个商品
        int productCount = random.nextInt(3) + 1;
        
        for (int i = 0; i < productCount; i++) {
            Long productId = createRandomProduct(i);
            createdProductIds.add(productId);
        }

        // 随机预热1个商品的库存到Redis
        if (!createdProductIds.isEmpty()) {
            Long randomProductId = createdProductIds.get(random.nextInt(createdProductIds.size()));
            inventoryService.preloadStock(randomProductId);
        }

        System.out.println("========================================");
        System.out.println("【随机生成】" + productCount + "个商品");
        System.out.println("【商品IDs】" + createdProductIds);
    }

    /**
     * 创建随机商品
     */
    private Long createRandomProduct(int seed) {
        String[] categories = {"水果", "零食", "饮料", "生鲜", "数码"};
        String category = categories[random.nextInt(categories.length)];

        Product product = new Product();
        product.setName(category + "礼包-" + (seed + 1));
        product.setCategory(category);
        product.setSeckillEnabled(true);
        product.setSeckillStartTime(LocalDateTime.now().minusHours(1));
        product.setSeckillEndTime(LocalDateTime.now().plusHours(2));
        product.setCreatedAt(LocalDateTime.now());
        
        product = productRepository.save(product);
        Long productId = product.getId();

        // 随机生成 1-5 个SKU
        int skuCount = random.nextInt(5) + 1;
        String[] origins = {"山东", "江西", "海南", "新疆", "湖北", "陕西", "福建"};
        String[] units = {"装", "斤", "个", "盒"};

        for (int j = 0; j < skuCount; j++) {
            createRandomSku(productId, origins, units);
        }

        return productId;
    }

    /**
     * 创建随机SKU
     */
    private void createRandomSku(Long productId, String[] origins, String[] units) {
        String[] names = {"精选", "特级", "A级", "优选", "精品"};
        String namePrefix = names[random.nextInt(names.length)];
        
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuName(namePrefix + "商品");
        sku.setSpec((random.nextInt(20) + 1) + units[random.nextInt(units.length)]);
        sku.setOrigin(origins[random.nextInt(origins.length)]);
        sku.setPrice(new BigDecimal(random.nextInt(200) + 20));
        sku.setSeckillPrice(new BigDecimal(random.nextInt(100) + 10));
        sku.setStock(random.nextInt(100) + 10);
        sku.setReservedStock(random.nextInt(10));
        sku.setLockedStock(0);
        sku.setStatus(1);
        sku.setCreatedAt(LocalDateTime.now());
        
        productSkuRepository.save(sku);
    }

    /**
     * 测试一：查看随机生成的商品
     */
    @Test
    public void testShowRandomProducts() {
        System.out.println("\n===== 随机商品列表 =====");
        
        List<Product> products = productRepository.findAll();
        
        for (Product p : products) {
            List<ProductSku> skus = productSkuRepository.findByProductId(p.getId());
            System.out.println("----------------------------------------");
            System.out.println("【商品】" + p.getName() + " (ID=" + p.getId() + ")");
            System.out.println("【SKU数】" + skus.size());
            
            int totalStock = 0;
            for (ProductSku sku : skus) {
                System.out.printf("  - %s %s | 原价%s 秒杀价%s 库存%d%n", 
                    sku.getOrigin(), sku.getSkuName(), sku.getPrice(), 
                    sku.getSeckillPrice(), sku.getStock());
                totalStock += sku.getStock();
            }
            System.out.println("【总库存】" + totalStock);
        }
        
        System.out.println("========================================");
        
        assertFalse(products.isEmpty());
    }

    /**
     * 测试二：随机查询商品库存
     */
    @Test
    public void testQueryRandomStock() {
        System.out.println("\n===== 随机查询库存 =====");
        
        if (createdProductIds.isEmpty()) {
            System.out.println("无可用商品");
            return;
        }
        
        for (Long productId : createdProductIds) {
            Map<String, Object> stock = inventoryService.getProductStock(productId);
            System.out.println("【商品ID=" + productId + "】" + stock);
        }
        
        System.out.println("========================================");
    }

    /**
     * 测试三：随机用户抢随机商品
     */
    @Test
    public void testRandomUserSeckill() {
        System.out.println("\n===== 随机用户抢购 =====");
        
        if (createdProductIds.isEmpty()) {
            System.out.println("无可用商品");
            return;
        }
        
        // 随机选一个商品
        Long productId = createdProductIds.get(random.nextInt(createdProductIds.size()));
        
        // 随机用户
        long userId = 10000L + random.nextInt(1000);
        
        com.example.seckill.dto.SeckillRequest request = 
            new com.example.seckill.dto.SeckillRequest();
        request.setUserId(userId);
        request.setProductId(productId);
        
        var response = inventoryService.seckillByProduct(request);
        
        System.out.println("----------------------------------------");
        System.out.println("【商品ID】" + productId);
        System.out.println("【用户】" + userId);
        System.out.println("【结果】code=" + response.getCode() + ", " + response.getMessage());
        System.out.println("========================================");
    }

    /**
     * 测试四：大批量随机用户抢单个SKU
     */
    @Test
    public void testMassRandomUsers() throws InterruptedException {
        System.out.println("\n===== 大批量随机用户 =====");
        
        if (createdProductIds.isEmpty()) {
            System.out.println("无可用商品");
            return;
        }
        
        // 选第一个商品，找它的第一个SKU
        Long productId = createdProductIds.get(0);
        List<ProductSku> skus = productSkuRepository.findByProductId(productId);
        if (skus.isEmpty()) return;
        
        Long skuId = skus.get(0).getId();
        
        // 随机数量的并发用户 (20-50人)
        int userCount = random.nextInt(30) + 20;
        var latch = new java.util.concurrent.CountDownLatch(userCount);
        int[] success = {0};
        int[] soldOut = {0};
        
        for (int i = 0; i < userCount; i++) {
            final long userId = 20000L + i;
            new Thread(() -> {
                try {
                    var request = new com.example.seckill.dto.SeckillRequest();
                    request.setUserId(userId);
                    request.setSkuId(skuId);
                    
                    var response = inventoryService.seckillBySku(request);
                    
                    synchronized (success) {
                        if (response.getCode() == 200) {
                            success[0]++;
                            System.out.println("【成功】用户" + userId);
                        } else if (response.getCode() == 402) {
                            soldOut[0]++;
                            System.out.println("【售罄】用户" + userId);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        latch.await();
        
        Integer remaining = inventoryService.getSkuStock(skuId);
        System.out.println("----------------------------------------");
        System.out.println("【SKU】" + skuId);
        System.out.println("【总请求】" + userCount);
        System.out.println("【成功】" + success[0]);
        System.out.println("【售罄】" + soldOut[0]);
        System.out.println("【剩余】" + remaining);
        System.out.println("========================================");
    }
}