local stockKey = KEYS[1]
local usersKey = KEYS[2]
local userId = ARGV[1]

-- Return code 2 means this user has already bought this activity.
if redis.call('sismember', usersKey, userId) == 1 then
    return 2
end

-- Return code 1 means stock is empty or was never warmed up.
local stock = tonumber(redis.call('get', stockKey))
if stock == nil or stock <= 0 then
    return 1
end

-- These two commands are atomic because the whole Lua script runs as one Redis operation.
redis.call('decr', stockKey)
redis.call('sadd', usersKey, userId)
return 0
