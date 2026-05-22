package com.example.seckill.constant;

public final class GlobalConstants {

    private GlobalConstants() {
    }

    // Redis keys
    public static final String SKU_STOCK_KEY = "sku:stock:";
    public static final String USER_BUY_KEY = "user:buy:";
    public static final String ACTIVITY_KEY = "activity:";

    // Lua script for atomic stock deduction
    public static final String STOCK_DEDUCT_SCRIPT = """
        local key = KEYS[1] 
        local user_key = KEYS[2]
        local quantity = tonumber(ARGV[1])
        local user_id = ARGV[2]
        local max_per_user = tonumber(ARGV[3])
        
        if redis.call('EXISTS', user_key) > 0 then
            return -1
        end
        
        local stock = redis.call('GET', key)
        if not stock or tonumber(stock) < quantity then
            return -2
        end
        
        redis.call('DECRBY', key, quantity)
        redis.call('SET', user_key, quantity, 'EX', 86400)
        
        return 1
        """;

    // HTTP
    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;
}