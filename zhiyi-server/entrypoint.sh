#!/bin/sh
set -x

# 构建镜像时 javaOpts 可能写入 -Dspring.profiles.active，会覆盖部署环境变量，此处先剥离
JAVA_OPTS_WITHOUT_PROFILE=$(echo "$JAVA_OPTS" | sed 's/-Dspring\.profiles\.active=[^ ]*//g' | sed 's/  */ /g' | sed 's/^ *//;s/ *$//')

# 部署阶段通过 SPRING_PROFILES_ACTIVE 指定运行环境（test / pro 等）
if [ -n "$SPRING_PROFILES_ACTIVE" ]; then
  PROFILE_JAVA_OPT="-Dspring.profiles.active=$SPRING_PROFILES_ACTIVE"
else
  PROFILE_JAVA_OPT=""
fi

echo "JAVA_OPTS=${JAVA_OPTS_WITHOUT_PROFILE} ${PROFILE_JAVA_OPT}"
exec java -Dfile.encoding=UTF-8 $JAVA_OPTS_WITHOUT_PROFILE $PROFILE_JAVA_OPT -jar /app/zhiyi-server-1.0.0-SNAPSHOT.jar "$@"
