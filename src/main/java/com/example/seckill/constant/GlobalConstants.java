package com.example.seckill.constant;

public final class GlobalConstants {

    private GlobalConstants() {
    }

    // Redis keys (带Hash Tag解决集群问题)
    public static final String SKU_STOCK_KEY = "{seckill}:stock:";
    public static final String USER_BUY_KEY = "{seckill}:user:";
    public static final String ACTIVITY_KEY = "{seckill}:activity:";

    // Lua script for atomic stock deduction
    public static final String STOCK_DEDUCT_SCRIPT = """
        local stock_key = '{' .. KEYS[1] .. '}:stock:' .. ARGV[1]
        local user_key = '{' .. KEYS[1] .. '}:user:' .. ARGV[1] .. ':' .. ARGV[2]
        
        -- 检查用户是否已抢购
        if redis.call('EXISTS', user_key) > 0 then
            return -1
        end
        
        -- 检查库存
        local stock = redis.call('GET', stock_key)
        if not stock or tonumber(stock) < tonumber(ARGV[3]) then
            return -2
        end
        
        -- 扣减库存并设置用户标记
        redis.call('DECRBY', stock_key, tonumber(ARGV[3]))
        redis.call('SET', user_key, ARGV[3], 'EX', 86400)
        
        return 1
        """;

    // HTTP
    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;
}