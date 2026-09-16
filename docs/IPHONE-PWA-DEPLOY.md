# iPhone PWA 部署步骤

## 一、发布静态网站

1. 使用 GitHub Desktop 打开 CampusSchedule 仓库，确认 `web/` 和 `.github/workflows/web-pages.yml` 已包含在改动中。
2. 填写提交说明，例如 `Release iPhone PWA 0.6.0 beta`，点击 **Commit to main**，再点击 **Push origin**。
3. 打开 GitHub 仓库网页，进入 **Settings → Pages**。
4. 在 **Build and deployment** 的 Source 中选择 **GitHub Actions**。不要选择从分支直接发布。
5. 打开仓库的 **Actions**，进入 `Deploy timetable web app`，等待 build 和 deploy 都显示绿色。
6. 访问 `https://xiaoyle.github.io/CampusSchedule/`。若仍显示旧版，在应用中接受更新提示，或关闭主屏幕应用后重新打开。

`main` 分支中只要 `web/**` 或部署工作流发生变化，GitHub Actions 就会自动运行测试、构建并发布。网站更新不需要重新生成 APK。

## 二、iPhone 安装测试

1. 用 Safari 打开网站，点击分享按钮。
2. 选择“添加到主屏幕”，名称保持“中大课表”。
3. 返回主屏幕，从新图标打开。
4. 先进入“我的 → 课表与导入”导入课表，再检查离线重开、备份、Apple 日历和快捷指令。
5. 如果此前导入的数据只在 Safari 中出现，请在 Safari 导出完整备份，再到主屏幕应用恢复。两个入口的数据空间不能保证自动共享。

## 三、可选 Web Push 服务

静态 GitHub Pages 无法定时发送通知。若需要 Web Push，另行部署 `push-server/`：

1. 在本机进入 `push-server/`，执行 `npx web-push generate-vapid-keys`，妥善保存公钥和私钥。
2. 准备一个支持 Node.js 22、HTTPS 和持久磁盘的服务。容器启动端口为 `8080`，数据目录为 `/data`。
3. 设置 `VAPID_PUBLIC_KEY`、`VAPID_PRIVATE_KEY`、`VAPID_SUBJECT` 和 `ALLOWED_ORIGIN=https://xiaoyle.github.io`。
4. 使用目录内 Dockerfile 部署，并确认访问 `/health` 返回正常状态。
5. 在主屏幕 Web App 的“我的 → 个性化与设置 → 推送服务配置”填写 HTTPS 地址和 VAPID 公钥，保存后由用户主动点击“开启 Web 通知”。

推送服务只保存匿名订阅、触发时间、密文和随机设备令牌。服务器重建时必须保留 `/data`，否则所有订阅和提醒会丢失。

## 四、回滚

若新版本出现严重问题，在 GitHub Desktop 中撤销对应提交并 Push。GitHub Actions 会重新发布上一版。数据结构升级具有向前迁移逻辑，但回滚前仍建议用户导出完整备份。
