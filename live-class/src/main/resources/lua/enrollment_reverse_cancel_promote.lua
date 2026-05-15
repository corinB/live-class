-- 보상 원자 스크립트: canceller를 enrolled에 복귀, promoted를 waitlist에 복귀 (단일 Lua 트랜잭션)
-- KEYS[1]=enrolled:{classId}, KEYS[2]=waitlist:{classId}
-- ARGV[1]=cancellerId, ARGV[2]=cancellerScore, ARGV[3]=promotedId (빈 문자열이면 skip), ARGV[4]=promotedScore, ARGV[5]=wasConfirmed (0/1)
redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
if ARGV[3] ~= '' then
  redis.call('ZREM', KEYS[1], ARGV[3])
  redis.call('ZADD', KEYS[2], ARGV[4], ARGV[3])
end
return 1
