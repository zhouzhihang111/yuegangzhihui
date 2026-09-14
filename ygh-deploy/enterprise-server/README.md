# 企业服务器配置参考

本目录保留全量服务器 Compose 配置作为架构参考，不提供发布、健康检查或回滚脚本，也不是本次客户手工部署入口。客户环境按项目外部 `../../../跨境智汇AI知识库系统-项目文档/deployment` 的编号顺序逐个安装基础组件，在 IDEA 中逐个启动 Java 服务。

生产平台确需验证此 Compose 时，由运维人员在当前终端逐项注入 Secret 管理系统提供的环境变量，再分别执行 `docker compose config`、`docker compose pull 服务名` 和 `docker compose up -d 服务名`。不得创建项目 `.env`，不得一次启动全部服务。

编排包含 14 个 Java 服务、MySQL、Redis、Nacos、RocketMQ、Seata、PGVector、Elasticsearch，以及 Prometheus、Alertmanager、Grafana 和 Node Exporter。告警规则覆盖服务不可用、HTTP 错误率、P95 延迟、JVM 堆、数据库连接池和磁盘水位。数据库和账号不会由脚本创建，必须先按 MySQL 文档逐条创建、授权并验证。

生产环境应将 MySQL、Redis、Elasticsearch、RocketMQ、Nacos 和对象存储替换为高可用集群或云托管服务。应用镜像不可使用 `latest` 发布，必须指定不可变版本或 digest。
