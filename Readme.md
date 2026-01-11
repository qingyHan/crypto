# 加密货币异常交易实时预警系统

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.8-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Flink](https://img.shields.io/badge/Apache%20Flink-2.0.0-blue.svg)](https://flink.apache.org/)
[![Vue](https://img.shields.io/badge/Vue.js-3.4-green.svg)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

一个基于大数据技术栈的加密货币实时监控与异常预警系统。通过WebSocket实时采集交易所数据，使用Apache Flink进行流式处理，支持多种预警策略配置。

> ⚠️ 本项目仍在持续优化中。。。

## 📸 系统截图

<!-- 待补充系统截图 -->

## ✨ 功能特性

- 🔄 **实时数据采集** - WebSocket连接OKX交易所，毫秒级数据获取
- ⚡ **流式数据处理** - Apache Flink实时计算K线、检测异常
- 🚨 **多策略预警** - 价格暴涨/暴跌、交易量异常、巨鲸交易检测
- 📊 **可视化界面** - Vue 3 + Element Plus构建的现代化Web界面
- 📈 **技术指标** - RSI、MACD等技术分析指标
- 🔬 **策略回测** - 历史数据策略验证
- 🐳 **一键部署** - Docker Compose容器化部署

## 🛠️ 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 数据采集 | OKX WebSocket API | - |
| 消息队列 | Apache Kafka | 7.8.0 (KRaft) |
| 流处理 | Apache Flink | 2.0.0 |
| 关系存储 | MySQL | 8.0 |
| 时序存储 | ClickHouse | 24.11 |
| 缓存 | Redis | 7.x |
| 后端框架 | Spring Boot | 3.5.8 |
| 前端框架 | Vue.js + Element Plus | 3.4 |
| 构建工具 | Maven / Vite | - |
| 容器化 | Docker Compose | - |

## 📁 项目结构

```
crypto/
├── crypto-common/          # 公共模块（实体、工具类）
├── crypto-collector/       # 数据采集服务
├── crypto-flink-jobs/      # Flink流处理作业
├── crypto-api-service/     # API服务（Spring Boot）
├── crypto-frontend/        # 前端（Vue 3）
├── docker/                 # Docker配置文件
├── docker-compose.yml     # Docker Compose编排
└── pom.xml               # Maven父POM
```

## 快速开始


### 一键部署

```bash

cd crypto-monitor
mvn clean package -DskipTests
docker-compose up -d
docker-compose ps
```

### 访问地址

| 服务 | 地址 |
|------|------|
| 前端界面 | http://localhost:3000 |
| API服务 | http://localhost:18080 |
| Flink Web UI | http://localhost:18081 |

## 监控的交易对

- BTC/USDT（比特币）
- ETH/USDT（以太坊）
- BNB/USDT
- SOL/USDT

## 预警策略类型

| 策略 | 说明 |
|------|------|
| 价格暴涨 | 短时间内价格快速上涨 |
| 价格暴跌 | 短时间内价格快速下跌 |
| 交易量异常 | 成交量超过均值N倍 |
| 巨鲸交易 | 单笔交易金额超过阈值 |

## 端口说明

| 服务 | 端口 |
|------|------|
| Frontend | 3000 |
| API | 18080 |
| Flink | 18081 |
| Kafka | 19092 |
| MySQL | 3307 |
| ClickHouse | 8124 |
| Redis | 6380 |


## 开发计划

- [ ] 支持更多交易所（Binance、Coinbase）
- [ ] 增加更多技术指标
- [ ] 支持更多预警策略
- [ ] 自定义策略丰富
- [ ] 用户登录认证
- [ ] 邮件消息通知


## 📄 License

MIT License
