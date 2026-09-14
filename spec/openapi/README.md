# OpenAPI 冻结文件

本目录保存经过真实运行实例导出并由自动化测试验证的接口契约。`auth-service-v1.json` 对应 `/api/v1/auth/**` 与 `/.well-known/jwks.json`，更新时必须先通过 Auth 模块测试和全 Reactor `clean verify`。

所有可运行服务通过 `/v3/api-docs` 和 `/swagger-ui.html` 暴露契约。更新冻结文件时不使用导出脚本，在 IDEA 中逐个启动服务，然后在 Windows PowerShell 对当前服务单独执行：

```powershell
Invoke-WebRequest -Uri 'http://127.0.0.1:8080/v3/api-docs' -OutFile '.\spec\openapi\gateway-service-v1.json'
Invoke-WebRequest -Uri 'http://127.0.0.1:8081/v3/api-docs' -OutFile '.\spec\openapi\auth-service-v1.json'
```

其余服务把端口和文件名替换为对应模块，一次只导出一个；打开生成的 JSON，确认存在 `openapi`、`info` 和 `paths` 后再处理下一服务。前端仍只能访问 Gateway；`spec/bruno` 中的密码和 Token 仅在运行时环境填写。
