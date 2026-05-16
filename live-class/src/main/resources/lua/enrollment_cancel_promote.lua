-- enrollment_cancel_promote.lua — cancel 시 ZREM enrolled + (CONFIRMED 였으면) ZPOPMIN waitlist + ZADD enrolled 원자 swap
-- KEYS[1]=enrolled:{classId}, KEYS[2]=waitlist:{classId}
-- ARGV[1]=classmateId, ARGV[2]=wasConfirmed (0/1)
-- return: 고정 길이 3 {cancellerScore, promotedId, promotedScore}
--   promotion 없으면 promotedId, promotedScore 는 빈 문자열
--   canceller 가 enrolled 에 없었으면 cancellerScore 도 빈 문자열
-- Java 파서: EnrollmentApplicationService.LuaCancelResult.from(List<String>) — return shape 변경 시 함께 갱신할 것
local cancellerScore = redis.call('ZSCORE', KEYS[1], ARGV[1])
redis.call('ZREM', KEYS[1], ARGV[1])
if ARGV[2] == '1' then
  local popped = redis.call('ZPOPMIN', KEYS[2], 1)
  if popped and #popped >= 2 then
    local promotedId = popped[1]
    local promotedScore = popped[2]
    redis.call('ZADD', KEYS[1], promotedScore, promotedId)
    return {cancellerScore or '', promotedId, promotedScore}
  end
end
return {cancellerScore or '', '', ''}
