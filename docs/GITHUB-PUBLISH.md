# GitHub 发布教程：安卓 APK 与 iPhone 网页版

项目使用一个公开仓库完成两类发布：仓库保存安卓与网页源码；GitHub Release 提供 APK；GitHub Pages 自动部署 `web/` 为 HTTPS PWA。

## 一、生成安全发布目录

在项目根目录运行：

```powershell
./scripts/prepare-github.ps1
```

脚本会在 `dist` 下生成 `github-release-0.8.0-时间`，其中：

- `repository/`：可公开上传的源码和文档，包含安卓工程、`web/` 和 Pages 工作流。
- `release-assets/`：APK、安卓源码 ZIP、SHA-256 校验文件。
- `发布说明.md`：创建 Release 时粘贴。

脚本采用允许列表，不复制 `private-fixtures`、构建缓存、APK、签名私钥、环境变量或本机工具。不要把整个日常开发目录上传。

## 二、创建仓库

1. 登录 GitHub，点击右上角 `+` → `New repository`。
2. Repository name 填 `CampusSchedule`。
3. Description 可填：`中大课表助手：课表导入、学习计划、专注复盘与桌面组件`。
4. 选择 `Public`。
5. 不初始化 README、`.gitignore` 或许可证，直接创建空仓库。

仓库尚未指定源码许可证。公开可见并不自动允许他人复制、修改和再分发；决定开放许可后再添加合适的 LICENSE。

## 三、上传源码

推荐使用 GitHub Desktop：

1. 打开 GitHub Desktop，选择 `File` → `Add local repository`。
2. 选择发布目录中的 `repository/`。若提示还不是仓库，选择创建仓库。
3. 提交信息填写 `Release v0.8.0 focus and achievements`。
4. 点击 `Publish repository`，名称使用 `CampusSchedule`，取消 `Keep this code private`。

也可在 GitHub 空仓库页面选择 `uploading an existing file`，将 `repository/` **内部的内容**拖入页面。上传后仓库根目录应直接看到 `README.md`、`app/`、`core/`、`web/` 和 `.github/`。

## 四、启用 GitHub Pages

1. 进入仓库 `Settings` → `Pages`。
2. 在 `Build and deployment` 的 `Source` 中选择 `GitHub Actions`。
3. 进入仓库 `Actions`，打开 `Deploy timetable web app`。
4. 首次上传通常会自动运行；没有运行时点击 `Run workflow`。
5. 等 build 和 deploy 都变绿后，访问 `https://xiaoyle.github.io/CampusSchedule/`。

工作流执行 `npm ci`、网页测试和 Vite 构建，再部署 `web/dist`。仓库名必须保持 `CampusSchedule`，否则还要同步修改 `web/vite.config.ts` 中的 `/CampusSchedule/` 路径。

## 五、发布安卓 0.8.0

1. 打开仓库右侧 `Releases` → `Draft a new release`。
2. 新建标签 `v0.8.0`，Target 选择 `main`。
3. 标题填写 `中大课表助手 v0.8.0 · 专注轨迹与成就馆`。
4. 将发布目录中的 `发布说明.md` 粘贴到说明框。
5. 从 `release-assets/` 上传：
   - `CampusSchedule-0.8.0.apk`
   - `CampusSchedule-source-0.8.0.zip`
   - `SHA256SUMS-0.8.0.txt`
6. 先选择 `Save draft`，确认三个附件都完整，再发布。当前仍是同学试用版本，建议勾选 `Set as a pre-release`。

APK 应作为 Release 附件上传，不提交到仓库源码。GitHub 自动生成的 Source code 压缩包也不包含 APK。

## 六、发布后检查

- 使用未登录浏览器打开仓库 README、Release 和 Pages 地址。
- 从 Release 的 Assets 下载 APK，确认文件名和大小正常。
- iPhone Safari 打开 Pages，检查“添加到主屏幕”和离线启动。
- Android 覆盖安装后确认版本显示 0.8.0、原课表仍存在。
- 在 Issues 中提醒同学不要上传 NetID、密码、验证码、学号、原始课表或带个人信息的截图。

## 后续版本

每次发布先提升 `versionCode` 和 `versionName`，更新 README、CHANGELOG 与发布说明，使用同一长期签名构建新 APK，再创建新的 `vX.Y.Z` Release。不要替换已经公开版本的 APK；修复问题时发布新版本，便于用户确认来源和覆盖升级。

官方参考：[添加本地代码](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)、[管理 Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)、[配置 GitHub Pages 发布源](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)。
