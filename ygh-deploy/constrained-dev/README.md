# 资源受限 DEV 配置参考

本目录保存 Docker Compose、MySQL、RocketMQ 等配置参考，不提供安装、初始化、启停或检查脚本。客户部署不以本目录作为一键入口，必须按照项目外部 `../../../跨境智汇AI知识库系统-项目文档/deployment` 中编号 00 至 12 的文档逐项操作。

## 运行位置

- 虚拟机 `192.168.154.10`：`core`（MySQL、Redis、Nacos）常驻；`ai-data`（PGVector）按需。
- Windows Docker Desktop：`mall-deps`（RocketMQ、Seata）或 `ai-deps`（Elasticsearch），二者互斥运行。
- Docker Desktop/WSL2 上限：3GB 内存、4 CPU、1GB Swap，且不随 Windows 自动启动。

## 虚拟机手工操作

MySQL、Redis、Nacos 和 PGVector 分别按 02、03、04、05 号文档使用单条 `docker pull`、`docker run`、`docker stop`、`docker start`、`docker logs` 和数据库客户端命令操作。数据库、账号、授权、备份与恢复均在对应组件文档中逐条执行。禁止使用 `docker compose down -v`。

## Windows 命令

RocketMQ、Seata 和 Elasticsearch 分别按 08、09、10 号文档逐个创建和启停容器。切换场景前在 PowerShell 执行 `docker ps`，人工确认上一场景容器已经停止且 Docker Desktop 剩余内存满足下一组件要求。

在 Docker Desktop 3GB 内存上限下联调 AI 时，Elasticsearch 与全部 Java 服务无法同时常驻。保留
`gateway/auth/user/system/product/order/knowledge/search/ai`，并停止
`inventory/wallet/training/notification/admin` 后再启动 `ai-deps`；切换模块时重新启动对应服务。

客户手工部署不创建 `.env`。密码和密钥只填写到 Docker Desktop 容器参数、IDEA Run Configuration 的 Environment variables 以及客户密码管理器中。

## 本地 Java 启动器 YAML 配置

Windows 本地联调可以使用 `tools/local-runner/LocalServiceLauncher` 读取扁平 YAML 配置，避免在
PowerShell 命令中反复粘贴环境变量。示例文件位于：

```text
tools/local-runner/ygh-core-services.yaml.example
```

使用时复制到仓库外，例如：

```text
E:/ygh-secrets/env/ygh-core-services.yaml
```

YAML 只支持扁平键值格式：

```yaml
YGH_AUTH_PORT: 8081
YGH_AUTH_DB_APP_PASSWORD: replace-me
```

启动前先校验配置文件：

```powershell
java -cp tools\local-runner\target\classes com.yuegang.zhihui.tools.LocalServiceLauncher --env E:\ygh-secrets\env\ygh-core-services.yaml --check
```

真实密钥文件不得提交。仓库内临时测试文件 `ygh-deploy/constrained-dev/.env.yaml` 已被 `.gitignore`
忽略，只允许用于本机测试。

## WSL 与 Docker Desktop

Docker Desktop 运行且已启用 Ubuntu 集成时，禁止执行 `wsl --terminate Ubuntu`。该操作会直接杀掉 Docker 的发行版代理并触发 `DockerDesktop/Wsl/ExecError`。

需要冷重启 WSL 时必须按以下顺序：

```powershell
docker desktop stop
wsl --shutdown
wsl -d Ubuntu --exec true
docker desktop start
```

不得使用 `wsl --unregister`，也不得删除 Ubuntu 或 `docker-desktop` 发行版。
