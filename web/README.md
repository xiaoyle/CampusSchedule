# 中大课表网页版 0.1.0

**xiaoyle 制作 · 非中山大学官方应用**

访问 https://xiaoyle.github.io/CampusSchedule/ 。推荐 iOS 17 及以上。

## iPhone 使用

1. 用 Safari 打开网站，点击「添加到主屏幕」阅读引导。
2. Safari 分享 → 添加到主屏幕；若显示「作为 Web App 打开」，请开启。
3. 从主屏幕「中大课表」图标打开，进入「导入与设置」。
4. 确认第一教学周周一。选择 Word／文字 PDF 文件，预览后确认保存。
5. 等待顶部显示「已可离线使用」。此后短暂断网可打开并查看已保存课表。

Safari 和主屏幕应用可能使用不同的数据空间，请先添加再导入。在 Safari 已有课表时，可「导出课表备份」，从主屏幕应用重新导入 JSON 文件。

## 学校登录辅助导入

网页无法直接读取另一个域名的登录课表，因此使用 Safari 分享菜单的快捷指令。

打开网站中的[快捷指令配置教程](https://xiaoyle.github.io/CampusSchedule/shortcut-guide.html)，按步骤新建「中大课表提取」：接收 Safari 网页 → 在网页上运行 JavaScript（替换为 `public/shortcut.js`）→ 拷贝结果至剪贴板 → 显示完成提醒。

用户自行在学校页面登录、完成二次验证，进入完整课表，选择全部周次。分享 → 运行快捷指令 → 从主屏幕图标打开课表 → 粘贴课表 → 预览确认。

快捷指令不会自动打开已经安装的 PWA。需要用户切回主屏幕应用并粘贴。无可安装的 iCloud 分享链接；必须在 Apple 设备上创建和验证后才能补充。学校真实流程待 iPhone 验证。

## 本地开发与部署

```powershell
cd web
npm ci
npm run dev
npm test
npm run build
npm run preview
```

Node.js 22 或更新版本。浏览器访问终端显示的 `/CampusSchedule/` 地址。构建自动复制 PDF.js 字体、CMap；产物不依赖外部 CDN。提交到 main 后 `.github/workflows/web-pages.yml` 自动测试、构建、部署 Pages。仓库 Settings → Pages 使用 GitHub Actions。

`vite.config.ts` 中 base、Manifest 的 id/start_url/scope 固定为 `/CampusSchedule/`，如更换站点路径须一起修改。更换域名会产生新的本地数据空间。

## 结构与接口

- `src/model.ts`：统一课程类型、周次计算、表格快照解析与受限 JSON 输入校验。
- `src/word.ts`：Flat OPC Word XML 与 DOCX 表格解析；不执行 XML 外部实体。
- `src/pdf.ts`、`pdf-geometry.ts`：本地 PDF.js 提取文字和描边，再按表格线还原跨页课程；扫描件、旋转及加密 PDF 不支持。
- `src/store.ts`：IndexedDB，写入成功后才替换界面状态；不因缓存升级删除课表。
- `src/main.tsx`、`style.css`：中文 React 界面、原生表单、统一课程卡与对话框。
- `public/shortcut.js`：仅限指定学校完整课表路由读取，不读取登录表单、Cookie 或完整页面。

导入格式：`{format:"campus-schedule",version:1,snapshot:{title,allWeeks,rows}}`；单元格 `{text,rowSpan,colSpan}`。备份格式使用相同 format/version，字段 `schedule` 替代 snapshot。快照最多 80 行、256 列、6000 单元格；粘贴最多 200 万字符，文件最多 8 MB。导入始终先预览。

## 隐私与限制

文件、结构化课表及剪贴板内容仅在本设备处理，不上传服务器，不进入 URL。无自建账号、分析统计或广告。GitHub Pages 托管方会处理网站访问请求；点击学校链接后由学校处理登录及其 Cookie。数据备份会包含课程、教师及地点，由用户自行保存。

清除浏览器数据、移除 Web App、存储空间不足或系统回收均可能影响本机数据；持久存储申请不是永久保存保证。请保留原文件或备份。

本阶段不包含后台闹铃、原生桌面组件、云同步、个人调课编辑或安卓 DIY 背景迁移。图标用于打开课表，不在图标中实时显示课程。

## 验证

Windows 上运行 `npm test`。私有课表样本不发布，缺少样本时私有回归测试跳过；公开合成用例仍执行。

`node browser-check.mjs` 在 Edge 中验证实际文件导入、预览保存、重新打开、损坏文件保护、离线壳与离线 PDF 导入，以及不同窗口宽度。运行前先 `npm run build`、`npm run preview -- --port 4173`，并在仓库根目录准备私有样本及 `.tools` 输出目录。

iPhone 主屏幕安装、刘海与手势区、真实 Safari 登录和快捷指令执行需用户真机验证，Windows 浏览器结果不能代替真机验收。
