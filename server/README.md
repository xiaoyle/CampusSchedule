# 中大课表助手社区服务 0.14.0

Kotlin/Ktor REST 服务，使用 MySQL、Argon2id、JWT短期访问令牌和可轮换刷新令牌。服务不读取安卓本机的课表、个人资料或草稿；只有用户主动发布的标题、正文、标签和公开昵称会上传。

## 本地运行

1. 复制 `.env.example` 中的变量到本机环境，替换全部示例密码。
2. 执行 `docker compose up --build`。
3. 访问 `http://localhost:8080/health`。首次使用 `ADMIN_BOOTSTRAP_INVITE` 注册的账号获得管理员角色，该邀请码最多使用一次。

服务启动时由 Flyway 自动执行 `src/main/resources/db/migration`。数据库和令牌密钥不能提交到 GitHub。

## CloudBase 部署

详细步骤见 [CloudBase 部署教程](../docs/CLOUDBASE-COMMUNITY.md)。服务监听环境变量 `PORT`，CloudRun 可直接从本目录的 Dockerfile 构建。部署后，在安卓社区页右上角填写 CloudBase HTTPS 地址进行内测。

## 管理与接口

- `openapi.yaml` 描述公开浏览、账号、帖子、收藏、评论、举报及管理员接口。
- `admin/index.html` 是无构建依赖的简易管理页，可放入 CloudBase 静态托管；管理员访问令牌只保存在页面内存。
- 管理操作写入 `moderation_audit`；删除帖子、评论和注销账号使用软删除。

邮件找回需在正式公开前接入邮件服务并完成邮箱验证。当前内测注册允许填写邮箱，但不会发送验证邮件，也不提供未经验证的密码重置入口。
