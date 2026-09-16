# GitHub Desktop 发布工作流

本项目用同一个 GitHub 仓库保存安卓和网页版源码；GitHub Release 提供 APK，GitHub Pages 继续部署 `web/`。0.14.0 增加安卓社区与 `server/`，网页版功能未修改，因此 Pages 无需重新配置。社区服务需按 `CLOUDBASE-COMMUNITY.md` 另行部署。

## 1. 生成发布目录

在项目根目录运行：

```powershell
./scripts/prepare-github.ps1
```

脚本已经按 UTF-8 BOM 保存，并避免使用新版 .NET 独有的路径函数，可直接在 Windows PowerShell 5.1 中运行。如果系统限制脚本执行，可使用：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-github.ps1
```

若终端出现整行中文乱码并报告 `ParserError`，说明拿到的是修复前的脚本；请用当前仓库中的 `scripts/prepare-github.ps1` 覆盖后重试。

脚本会在 `dist` 生成 `github-release-0.14.0-时间/`：

- `repository/`：可以覆盖到 GitHub 仓库的公开源码。
- `release-assets/`：APK、源码 ZIP、SHA-256 校验文件。
- `发布说明.md`：创建 Release 时粘贴。

发布目录不会复制私有课表样本、构建缓存、签名私钥、本机工具或账号资料。

## 2. 用 GitHub Desktop 更新现有仓库

1. 打开 GitHub Desktop，选择现有 `xiaoyle/CampusSchedule` 仓库。
2. 点击 **Fetch origin**，有远端更新时先 **Pull origin**。
3. 在资源管理器中打开本地仓库目录。
4. 将新生成的 `repository/` **内部全部内容**复制到该仓库根目录，选择覆盖同名文件。不要把 `repository` 文件夹本身套进去。
5. 回到 GitHub Desktop 检查 Changes。仓库根目录应能看到 `README.md`、`app/`、`core/`、`benchmark/`、`server/`、`web/`、`docs/` 和 `.github/`。
6. 确认列表中没有 `private-fixtures`、`.tools`、`build`、`node_modules`、APK、密钥、个人课表或账号截图。
7. Summary 填 `Release v0.14.0 community beta and image timetable import`，点击 **Commit to main**。
8. 点击 **Push origin**。

## 3. 创建 v0.14.0 Pre-release

1. 浏览器打开仓库，进入 **Releases → Draft a new release**。
2. 新建标签 `v0.14.0`，Target 选择 `main`。
3. 标题填写 `中大课表助手 v0.14.0 · 图片课表与社区内测`。
4. 粘贴发布目录中的 `发布说明.md`。
5. 从 `release-assets/` 上传三个附件：
   - `CampusSchedule-0.14.0.apk`
   - `CampusSchedule-source-0.14.0.zip`
   - `SHA256SUMS-0.14.0.txt`
6. 勾选 **Set as a pre-release**，先保存草稿检查附件，确认后发布。

APK 只作为 Release 附件，不提交到源码仓库。GitHub 自动生成的 Source code 也不是安卓安装包。

## 4. 发布后检查

- 在未登录窗口打开仓库 README 和 v0.14.0 Release。
- 从 Assets 下载 APK，确认文件名与大小正常。
- 覆盖安装后在“我的”确认版本为 0.14.0，旧课表仍存在。
- 依次检查图片课表校对、社区离线缓存、公开浏览、邀请码登录和笔记副本发布。
- Pages 若因源码 Push 自动运行，等待 Actions 变绿即可；本版本网页内容未改，无需重新配置 Pages。

以后发布只需：提升版本号 → 更新文档 → 构建与打包 → 运行 `prepare-github.ps1` → 覆盖本地仓库并 Push → 新建对应 Release。

