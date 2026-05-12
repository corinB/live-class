-- enrollment_apply.lua — Lua 1차 게이트: 중복 검사 + 정원 검사 + ZSET ADD 원자 실행
-- KEYS[1]=enrolled:{classId}, KEYS[2]=waitlist:{classId}, KEYS[3]=class:status:{classId}
-- ARGV[1]=capacity (number string), ARGV[2]=classmateId (UUID string), ARGV[3]=appliedAtNanos (number string)
local status = redis.call('GET', KEYS[3])
if not status then return 'CLASS_NOT_FOUND' end
if status ~= 'OPEN' then return 'CLASS_NOT_OPEN' end
if redis.call('ZSCORE', KEYS[1], ARGV[2]) then return 'DUPLICATE_ACTIVE' end
if redis.call('ZSCORE', KEYS[2], ARGV[2]) then return 'DUPLICATE_ACTIVE' end
local current = tonumber(redis.call('ZCARD', KEYS[1]))
local cap = tonumber(ARGV[1])
if current < cap then
  redis.call('ZADD', KEYS[1], ARGV[3], ARGV[2])
  return 'PENDING'
else
  redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2])
  return 'WAITLISTED'
end
