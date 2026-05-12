#!/usr/bin/env bash
# Spring Boot 4 가 자동 등록하는 빈 이름과 충돌하는 @Bean 메서드를 감지해 경고 (non-blocking)
set -euo pipefail

_payload=$(cat)

# Write 또는 Edit 도구가 아닌 호출은 통과
tool_name=$(printf '%s' "${_payload}" | sed -nE 's/.*"tool_name"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)
if [ -z "${tool_name}" ]; then exit 0; fi
if [ "${tool_name}" != "Write" ] && [ "${tool_name}" != "Edit" ]; then exit 0; fi

file_path=$(printf '%s' "${_payload}" | sed -nE 's/.*"file_path"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)

# .java 파일만 처리
case "${file_path}" in
  *.java) ;;
  *) exit 0 ;;
esac

if [ ! -f "${file_path}" ]; then exit 0; fi

# @Bean 메서드 이름 추출. 패턴 예시:
#     @Bean
#     public RedisCacheManager cacheManager(RedisConnectionFactory cf) { ... }
# awk 로 @Bean 다음 라인에서 메서드 이름 캡처.
bean_names=$(awk '
  /@Bean/ { getbean = 1; next }
  getbean {
    # 라인에 "public" 또는 "protected" + 토큰들 + 메서드명(opening paren) 패턴이면 메서드명 추출.
    if (match($0, /(public|protected)[[:space:]]+[A-Za-z_][A-Za-z0-9_<>?,[:space:]\.]*[[:space:]]+([a-zA-Z_][a-zA-Z0-9_]*)[[:space:]]*\(/, arr)) {
      print arr[2]
      getbean = 0
    } else if ($0 ~ /\{/) {
      # 메서드 시그니처 끝났는데 못 잡았으면 포기.
      getbean = 0
    }
  }
' "${file_path}" 2>/dev/null || true)

if [ -z "${bean_names}" ]; then exit 0; fi

# Spring Boot 4 가 자동 등록하는 빈 이름 (가장 흔한 충돌 후보)
known_beans="cacheManager redisConnectionFactory redisTemplate stringRedisTemplate objectMapper dataSource entityManagerFactory transactionManager taskExecutor taskScheduler"

conflicts=""
while IFS= read -r name; do
  [ -z "${name}" ] && continue
  for known in ${known_beans}; do
    if [ "${name}" = "${known}" ]; then
      conflicts="${conflicts} ${name}"
      break
    fi
  done
done <<< "${bean_names}"

# trim leading space
conflicts="${conflicts# }"

if [ -n "${conflicts}" ]; then
  cat >&2 <<EOF
[hook:post-write] ⚠ ${file_path}: Spring Boot 4 가 자동 등록하는 빈 이름과 충돌 가능: ${conflicts}.

Spring Boot 4 의 auto-config (DataRedisAutoConfiguration, CacheAutoConfiguration, JacksonAutoConfiguration 등) 가 같은 이름의 빈을 등록하면 BeanDefinitionOverrideException 으로 contextLoads 가 실패합니다.

회피 패턴.
  1. 메서드 이름을 다르게 (예: cacheManager → liveClassCacheManager) + @Primary 어노테이션
  2. Spring Boot default 를 그대로 사용 (별도 @Bean 정의 안 함)
EOF
fi

exit 0
