#
# zhiyi-server 镜像（JDK 8 + Spring Boot fat jar）
#
FROM openjdk:8
MAINTAINER zhiyi

COPY ./entrypoint.sh /entrypoint.sh
COPY ./target/zhiyi-server-1.0.0-SNAPSHOT.jar /app/zhiyi-server-1.0.0-SNAPSHOT.jar
ARG javaOpts
ENV TZ=Asia/Shanghai \
    LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8 \
    JAVA_OPTS=${javaOpts}
RUN chmod +x /entrypoint.sh
VOLUME /data
EXPOSE 4312
ENTRYPOINT ["/entrypoint.sh"]
