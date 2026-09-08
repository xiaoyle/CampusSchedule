# GitHub 发布教程

使用 GitHub Releases 发布安卓安装包，无需部署服务器，也不需要 GitHub Pages。

## 1. 准备目录

运行 `scripts/prepare-github.ps1` 会在 dist 中生成一个带时间戳的 `github-release-*` 文件夹：

- `repository`：允许公开的源码与文档，上传到仓库。
- `release-assets`：APK、源码 ZIP、校验文件，上传到 Release。
- `发布说明.md`：创建 Release 时复制其中正文。

不要上传整个日常开发目录，也不要把 APK 放进仓库 Code 中。私有课表、签名文件、工具与缓存均不在公开目录内。

## 2. 创建 GitHub 仓库

1. 登录 GitHub，点击右上角 `+` → `New repository`。
2. Repository name 填 `CampusSchedule`；描述填 `中大课表助手：安卓课表导入、桌面组件与 DIY · xiaoyle 制作`。
3. 选择 `Public`，表示所有人都能看见源码和下载公开 Release。
4. 不勾选初始化 README，不另选 .gitignore，也先不选择许可证，点击 `Create repository`。

本次没有替作者选择 MIT / Apache 等源码授权。公开可见和授权他人修改、分发是不同决定；若希望开放源码授权，可之后明确选定许可证。依赖与 Gradle wrapper 的原有许可仍适用。

## 3. 上传源码（网页操作）

1. 在空仓库页面点击 `uploading an existing file`；已有内容时点击 `Add file` → `Upload files`。
2. 打开准备目录中的 `repository`，全选其中的文件和子文件夹，拖入网页。注意上传的是里面的内容，不是外层 repository 文件夹，更不是 ZIP。
3. 检查根目录直接显示 `README.md`、`app`、`core`、`gradle`、`scripts` 等。应包括 `.gitignore` 和 `.github`。
4. Commit message 填 `Prepare v0.3.0 DIY preview release`，点击 `Commit changes`。

网页上传若因文件数量或网络失败，可分批上传文件夹；APK 不使用这个入口。也可用 GitHub Desktop 导入 repository 目录后提交发布。不要提交访问令牌或签名私钥。

## 4. 创建可下载版本

1. 仓库首页右侧找到 `Releases` → `Create a new release`（也可能显示 `Draft a new release`）。
2. Tag 填 `v0.3.0`，选择创建新标签；Target 选已上传源码的默认分支。
3. 标题填 `中大课表助手 v0.3.0 · 桌面 DIY 试用版`。
4. 将准备目录内 `发布说明.md` 的正文复制到说明框。
5. 将 `release-assets` 里面的 APK、源码 ZIP 和 SHA256 校验文件拖到附件区域，等待三个文件上传完成。
6. 勾选 `Set as a pre-release`。先点击 `Save draft` 检查内容，再点击 `Publish release`。

这里发布的是当前本机已验证签名的 APK。不要使用 GitHub Actions 临时生成的默认 debug APK 替换它，不同签名会让已有安装无法直接覆盖更新。

## 5. 发给同学

打开已发布的 Release，复制浏览器地址发给同学。提醒对方展开 Assets，下载 `CampusSchedule-0.3.0.apk`，不是 Source code。

发布后用未登录窗口确认页面和 APK 可访问。GitHub 自动生成的 Source code ZIP 不含 APK，APK 在 Release 附件中。

## 后续更新

- 修复后增加 versionCode、更新版本号和发布说明，保留同一包名和相同签名，发布新的版本标签；不要静默替换已经发布的旧 APK。
- 本机当前签名文件不要上传；请自行安全保存。正式面向更多用户发布前，再规划长期发布签名。
- 当前准备工作不包含自动构建发布流水线、应用商店上架或原生鸿蒙版。

官方参考：
- [管理 Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
- [上传项目文件](https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository)
- [添加本地代码](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)
