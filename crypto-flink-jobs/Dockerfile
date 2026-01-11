# Flink作业镜像（用于提交作业）
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

# 设置工作目录
WORKDIR /build

# 复制pom文件
COPY pom.xml .
COPY crypto-common/pom.xml crypto-common/
COPY crypto-flink-jobs/pom.xml crypto-flink-jobs/

# 下载依赖
RUN mvn dependency:go-offline -B -pl crypto-flink-jobs -am

# 复制源代码
COPY crypto-common/src crypto-common/src
COPY crypto-flink-jobs/src crypto-flink-jobs/src

# 构建项目（生成包含所有依赖的fat jar）
RUN mvn clean package -pl crypto-flink-jobs -am -DskipTests

# 使用轻量级基础镜像存放构建好的jar
FROM alpine:3.19

# 创建目录
WORKDIR /opt/flink-jobs

# 从构建阶段复制jar包
COPY --from=builder /build/crypto-flink-jobs/target/crypto-flink-jobs-*.jar .

# 说明：此镜像仅用于存储Flink作业jar包
# 实际运行时需要将jar包提交到Flink集群
# 使用方式: docker cp <container>:/opt/flink-jobs/crypto-flink-jobs-*.jar .

CMD ["echo", "Flink job jars are ready in /opt/flink-jobs/"]
