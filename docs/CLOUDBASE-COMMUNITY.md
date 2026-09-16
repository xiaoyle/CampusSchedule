# 0.14.0 社区内测：腾讯云 CloudBase 部署

## 需要准备

- 一个 CloudBase 环境和可由 CloudRun 私网访问的 MySQL 实例。
- 数据库名、用户名和独立强密码。
- 至少32字符的随机 `JWT_SECRET`，以及一次性管理员邀请码 `ADMIN_BOOTSTRAP_INVITE`。
- Node.js，仅用于安装官方 CloudBase CLI。

这些值只写入 CloudRun 的环境变量，不能写进源码、APK、Issue、截图或 GitHub Actions 日志。

## 部署 API

1. 安装并登录官方 CLI：

   ```powershell
   npm install -g @cloudbase/cli
   tcb login
   tcb env list
   ```

2. 在 CloudBase 创建 MySQL，并允许云托管服务通过私有网络访问。创建空数据库 `campus_schedule`。
3. 在 CloudRun 服务配置以下环境变量：`DATABASE_URL`、`DATABASE_USER`、`DATABASE_PASSWORD`、`JWT_SECRET`、`ADMIN_BOOTSTRAP_INVITE`。JDBC 地址示例位于 `server/.env.example`。
4. 进入 `server/`，执行：

   ```powershell
   ./scripts/deploy-cloudbase.ps1 -EnvironmentId 你的环境ID
   ```

   也可以在 CloudBase 控制台选择“云托管 → 新建服务 → 本地代码”，上传 `server/`，使用根目录 Dockerfile，端口设为8080并开启公网 WEB 访问。
5. 打开 `https://你的测试域名/health`，确认返回 `status: ok`。测试域名只用于邀请码内测。
6. 在安卓 App 的“社区”右上角配置该 HTTPS 根地址。注册第一个管理员后，从 CloudRun 移除 `ADMIN_BOOTSTRAP_INVITE` 环境变量。

CloudBase 官方 CLI 使用 `tcb cloudrun deploy` 部署容器服务，Dockerfile 会被自动识别；控制台也支持本地代码、环境变量、WEB访问和灰度版本。[CloudBase CLI](https://cloud.tencent.com/document/product/876/41539) · [CloudRun部署](https://docs.cloudbase.net/ai/agent-development/deployment/cloud-run) · [云托管控制台](https://cloud.tencent.com/document/product/876/46901)

## 管理页

将 `server/admin/` 上传到 CloudBase 静态托管。打开页面后填写 API 根地址和管理员的15分钟访问令牌，可创建邀请码、查看举报、隐藏帖子、删除评论和封禁账号。管理页不持久保存令牌。

## 上线前必须完成

- 以邀请码 Pre-release 小范围运行，检查日志、限流、数据库备份、举报处理和成本。
- 邮箱验证和找回密码当前未开放；接入邮件服务后再在客户端展示该入口。
- 正式公开前使用已备案自定义域名，补充社区隐私政策、用户协议、内容规范和未成年人保护说明。
- 定期轮换数据库密码和 JWT_SECRET；轮换 JWT_SECRET 会使所有访问令牌失效。
- 不在日志中记录请求正文、密码、刷新令牌、邮箱正文或笔记正文。
