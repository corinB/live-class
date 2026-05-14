-- enrollment_cancel_promote.lua — cancel 시 ZREM enrolled + (CONFIRMED 였으면) ZPOPMIN waitlist + ZADD enrolled 원자 swap
-- KEYS[1]=enrolled:{classId}, KEYS[2]=waitlist:{classId}
-- ARGV[1]=classmateId, ARGV[2]=wasConfirmed (0/1)
redis.call('ZREM', KEYS[1], ARGV[1])
if ARGV[2] == '1' then
  local popped = redis.call('ZPOPMIN', KEYS[2], 1)
  if popped and #popped >= 2 then
    local promotedId = popped[1]
    local promotedScore = popped[2]
    redis.call('ZADD', KEYS[1], promotedScore, promotedId)
    return {promotedId, promotedScore}
  end
end
return nil
