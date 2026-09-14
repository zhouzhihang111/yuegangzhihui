# 资源受限开发环境部署规格

## 1. 目标

在不升级笔记本硬件的前提下，最大化利用 2 vCPU、3.5GB 内存的 Rocky Linux 虚拟机，同时保持 Windows 本机可正常运行 IDEA、前端和必要的重型 Docker 依赖。所有非常驻组件必须按场景启停，不以“全量同时运行”作为资源受限 DEV 的目标。

## 2. 已确认资源

| 节点 | 地址 | CPU | 内存 | 磁盘 |
|---|---|---:|---:|---:|
| Windows 开发机 | VMware VMnet8 `192.168.154.1` | 16 逻辑处理器 | 15.2GB | F 盘约 610GB 可用 |
| Rocky Linux 10 VM | 服务地址 `192.168.154.10`；DHCP 管理地址当前为 `.129` | 2 vCPU | 3.5GB | 根分区约 12GB 可用 |

虚拟机能够访问 Windows 宿主机 `192.168.154.1`。

## 3. 运行分配

### 3.1 虚拟机

- `core`：MySQL、Redis、Nacos。
- `ai-data`：PostgreSQL 17 + PGVector 0.8.5，按需启动，数据卷保留在虚拟机。
- 后续 `mall`：商城相关 Java 服务。
- 后续 `ai-apps`：知识与 AI Java 服务。
- 后续 `training`：培训相关 Java 服务。

### 3.2 Windows 本机 Docker

- `mall-deps`：RocketMQ、Seata。
- `ai-deps`：Elasticsearch；与 `mall-deps` 互斥运行。

本机 Docker 组件仅服务当前资源受限 DEV。企业服务器清单仍须包含这些组件的服务器实例。

## 4. 资源上限

### 4.1 虚拟机常驻组件

| 组件 | 内存上限 |
|---|---:|
| MySQL | 640MB |
| Redis | 128MB |
| Nacos | JVM 堆 384MB，容器上限 768MB |
| PGVector（按需） | 384MB |

虚拟机至少保留 700MB 给操作系统与 Docker。启动 PGVector 前可用内存低于 700MB 时脚本必须中止。RocketMQ、Seata 和 Elasticsearch 不迁入该 3.5GB 虚拟机常驻运行。

### 4.2 本机

| 项目 | 上限 |
|---|---:|
| Docker Desktop/WSL2 | 3GB |
| Docker Desktop CPU | 4 个逻辑处理器 |
| Docker Desktop Swap | 1GB |
| IDEA JVM Heap | 2GB—2.5GB |

## 5. 网络约束

- 本机 Docker 发布端口绑定 `192.168.154.1`，不绑定所有接口。
- Windows 防火墙仅允许 VMware `192.168.154.0/24` 网段访问开发依赖端口。
- 虚拟机地址在写入配置前必须改为静态地址或 DHCP 保留。
- Secret 仅保存在本机和虚拟机的非 Git `.env`/secret 文件中。

## 6. Docker 安装策略

- Rocky Linux 10 采用 Docker 官方 CentOS Stream 10 RPM 仓库方式安装 Docker Engine、Buildx 和 Compose Plugin。
- 安装前检查冲突包、SELinux、cgroup v2、磁盘和网络。
- Docker daemon 启用日志轮转、Live Restore、BuildKit 和明确的数据根目录。
- 不关闭 SELinux 作为故障规避手段。

## 7. 当前闭环交付

1. 本机资源限制配置完成且有备份。
2. 虚拟机 Docker Engine 和 Compose 可用。
3. `core` profile 的 MySQL、Redis、Nacos 可启动并通过健康检查。
4. `ai-data` PGVector、本机 `mall-deps` 和 `ai-deps` 均已分别完成实机健康、连通性和资源验证。
5. 提供启动、停止、状态、备份和清理脚本。
6. 保存无密钥的验证结果和资源快照。

## 8. 禁止事项

- 不同时启动全部 profiles 后再观察是否 OOM。
- 不在其他 Docker 项目容器运行时启动本项目本机 profile。
- 不使用 `latest` 镜像。
- 不把真实密码写入 Compose 或 Git。
- 不删除用户已有 Docker 数据、VM 快照或未知配置。
- 不用 Swap 掩盖长期内存不足。
