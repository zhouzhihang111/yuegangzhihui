# 性能测试

`enterprise-api.js` 在专用性能账号和隔离数据环境中同时验证普通查询、模拟充值写入与商品搜索。测试不会接入真实资金，但会写入模拟钱包流水，禁止对生产数据库执行。

```powershell
$env:YGH_GATEWAY_URL = "https://sit.example.com"
$env:YGH_ACCESS_TOKEN = "<从专用性能账号取得，禁止写入文件>"
k6 run .\k6\enterprise-api.js
```

门禁为错误率 `<1%`、普通查询 P95 `<500ms`、普通写入与商品搜索 P95 `<1000ms`。结果 JSON/HTML 属于验收产物，不提交包含 Token 的命令历史或环境文件。
