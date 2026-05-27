-- 库存原子扣减脚本
-- KEYS[1]: 库存 key (sku:stock:{skuId})
-- KEYS[2]: 用户购买记录 key (sku:buy:{skuId}:{userId})
-- ARGV[1]: 购买数量

local stockKey = KEYS[1]
local userKey = KEYS[2]
local quantity = tonumber(ARGV[1])

-- 检查库存
local stock = redis.call('GET', stockKey)
if not stock then
    return -1  -- 库存未初始化
end

stock = tonumber(stock)
if stock < quantity then
    return -2  -- 库存不足
end

-- 检查用户是否已购买
if redis.call('EXISTS', userKey) > 0 then
    return -3  -- 已抢购
end

-- 原子扣减库存
redis.call('DECRBY', stockKey, quantity)

-- 标记用户已购买
redis.call('SET', userKey, quantity, 'EX', 86400)

return 1  -- 成功