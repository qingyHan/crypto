# 使用预构建的 JAR 文件
FROM eclipse-temurin:21-jre-alpine

# 设置时区
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 创建应用目录
WORKDIR /app

# 复制已构建的 FAT JAR 包
COPY crypto-collector/target/crypto-collector-1.0.0.jar app.jar

# 暴露端口（可选，collector不需要暴露端口）
# EXPOSE 8081

# 设置JVM参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD pgrep -f 'java.*crypto-collector' || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
