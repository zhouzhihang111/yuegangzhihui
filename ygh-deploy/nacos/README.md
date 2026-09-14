# Nacos 环境与配置规范

每个环境使用独立 namespace，不通过 group 模拟环境隔离：

| 环境 | namespace | group |
|---|---|---|
| 开发 | `ygh-dev` | `YGH_GROUP` |
| 集成测试 | `ygh-sit` | `YGH_GROUP` |
| 验收 | `ygh-uat` | `YGH_GROUP` |
| 生产 | `ygh-prod` | `YGH_GROUP` |

共享配置 Data ID 为 `ygh-common.yaml`，服务配置为 `${spring.application.name}.yaml`，灰度配置为 `${spring.application.name}-${label}.yaml`。配置只能保存非敏感参数；密码、Token、私钥和模型 API Key 必须由环境变量或 Secret 管理系统注入。

发布顺序固定为：创建 namespace → 导入共享配置 → 导入服务配置 → 校验配置监听 → 启动服务 → 验证注册实例。生产 namespace 禁止使用自动创建和默认密码。
