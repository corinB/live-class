-- enrollment_compensate.lua — DB INSERT 실패 시 ZSET 에 추가된 항목을 원자적으로 제거하는 보상 스크립트
-- KEYS[1]=enrolled:{classId}, KEYS[2]=waitlist:{classId}, ARGV[1]=classmateId
local r1 = redis.call('ZREM', KEYS[1], ARGV[1])
local r2 = redis.call('ZREM', KEYS[2], ARGV[1])
return r1 + r2
