# 粤港甄选 · 跨境智汇 AI 企业级分布式系统

面向粤港跨境商城的「商城 + 知识库 + AI 客服」一体化平台，基于 Spring Cloud Alibaba 的 14 微服务架构，Docker 容器化部署。

> 仓库已移除全部生产凭据、数据库数据与公司内部文档；运维脚本因含内网配置未入库。

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 / 框架 | Java 25 · Spring Boot 4.0.7 · Spring Cloud 2025.1.2 · Spring Cloud Alibaba 2025.1.0.0 |
| 数据 | MyBatis-Plus 3.5.16 · MySQL 8 · Redis 7 · PGVector + Elasticsearch 8 |
| 消息 | RocketMQ 5.3 |
| AI | LangChain4j 1.17 · DeepSeek 大模型 |
| 前端 | Vue 3 + TypeScript + Vite（ygh-web） |
| 工程化 | Maven 多模块 · Docker · Flyway · JaCoCo · springdoc-openapi |

## 架构亮点

- **微服务架构**：14 个微服务、每服务独立数据库；Nacos 注册发现；Gateway 统一 JWT 鉴权 + 服务间 HMAC-SHA256 内部签名
- **AI 客服**：LangChain4j 集成 DeepSeek，PGVector 语义检索 + ES 全文检索混合搜索，引用式回答不猜测
- **异步与安全**：RocketMQ 异步驱动订单履约；Redis 限流 + 登录保护；Argon2id 密码哈希 + AES-GCM 字段加密
- **数据库与权限**：Flyway 数据库迁移；RBAC 三级权限（USER / EMPLOYEE / ADMIN）+ 全量审计日志（traceId 追踪）；JaCoCo 覆盖率 ≥ 70%

## 模块结构

```
yuegang-zhihui-ai
├── ygh-dependencies   # BOM 依赖版本管理
├── ygh-common         # 公共组件：core / redis / security / web
├── ygh-platform       # 平台层：auth 认证服务、gateway 网关
├── ygh-applications   # 业务层（12 个服务）：
│   admin · ai · inventory · knowledge · notification · order ·
│   product · search · system · training · user · wallet
├── ygh-web            # 前端（Vue 3 + TS + Vite）
└── ygh-tests          # 测试工程
```

## 个人参与部分

- 后端微服务模块设计开发、核心接口开发与全流程联调
- RAG 智能客服（知识库检索与问答链路）
- AI 推荐功能开发、大模型 API 对接

## 安全说明

- 所有数据库密码 / API Key 均通过环境变量注入，仓库不含任何生产凭据
- 运维脚本（含内网 IP、初始化口令）与数据库数据未入库
- 密码重置 / Nacos 重建等脚本属内部运维资产，不随仓库分发
