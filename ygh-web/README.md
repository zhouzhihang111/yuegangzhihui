# 粤港甄选 Web 前端

本目录是从 Figma React 原型重构后的正式 Vue 3 前端工作区。原始压缩包仅作为产品视觉参考，不作为运行依赖。

- `apps/ygh-web-mall`：公共商城、知识中心、AI 客服、个人中心与员工培训。
- `apps/ygh-web-admin`：运营后台、商品/订单/知识/培训/RBAC/审计管理。
- `packages/ygh-web-shared`：统一响应、JWT 会话、权限、Axios Client、SSE 和共享类型。

所有浏览器请求只访问 Gateway；不得直连具体 Java 服务端口。

```powershell
pnpm install
pnpm type-check
pnpm build
pnpm dev:mall
pnpm dev:admin
```
