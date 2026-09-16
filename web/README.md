# 中大课表 iPhone PWA 0.6.0-beta.1

**xiaoyle 制作 · 非中山大学官方应用**

访问 <https://xiaoyle.github.io/CampusSchedule/>。推荐 iOS 17 及以上，也兼容现代 Android 和桌面浏览器。

## iPhone 使用

1. 用 Safari 打开网站，点“分享 → 添加到主屏幕”。
2. 从主屏幕“中大课表”图标打开。
3. 进入“我的 → 课表与导入”，导入 Word、DOCX、文字 PDF、图片或快捷指令课表。
4. 在预览或 OCR 校对页确认内容，保存后即可离线查看。
5. 在“设置”导出 Apple 日历，或在已部署匿名推送服务后开启 Web Push。

Safari 与主屏幕 Web App 的本地数据空间可能分开，因此建议先添加到主屏幕再导入。清理浏览器、换机或卸载前，请导出“完整备份 V2”。

## 已迁移功能

- 今日：首页课程、教学周、学期进度、未来七天负荷、优先待办、空档雷达和专注入口。
- 计划：周课表、学习月历、单次及每周自建课程、重复待办、自选星期、子任务、搜索筛选和本地笔记栏目。
- 导入：Word、DOCX、文字 PDF、Safari 快捷指令、JPG/PNG/WebP 图片 OCR；所有导入先预览或校对。
- 笔记：完整阅读/编辑页、700 毫秒草稿、纯文本/安全 Markdown、五种纸张与单篇外观。
- 专注：25/45/60 分钟倒计时，按时间戳恢复，完成记录用于学习统计与成就。
- 我的：头像、可选校园资料、三套主题、启动充能页、五个内置成就和自定义成就。
- 数据：IndexedDB 分仓储、旧版课表自动迁移、完整备份与恢复、Service Worker 离线壳。
- 提醒：Apple 日历 ICS；可选匿名加密 Web Push。推送服务未配置时不会显示为已启用。

## 学校网页辅助导入

PWA 无法跨域读取已登录的学校页面，因此继续使用 Safari 分享菜单快捷指令：

1. 在 Safari 登录学校，完成二次验证。
2. 进入课表查询，选择学期，等待任意一周课表显示完成；iPhone 页面不需要寻找“全部”选项。
3. 分享 → 从共享表单运行“中大课表”。
4. 回到主屏幕应用，粘贴、预览并保存。

[快捷指令配置教程](https://xiaoyle.github.io/CampusSchedule/shortcut-guide.html)使用 `public/shortcut.js`。电脑版已显示“全部”时直接读取表格；iPhone 单周界面则在同一登录会话内调用学校课表页自身使用的 `week=99` 查询，生成完整学期数据。脚本不读取登录表单、Cookie、姓名或学号。可安装的 iCloud 快捷指令链接必须在 Apple 设备上创建并验证，本仓库不虚构分享链接。

## 图片识别

图片识别使用 Tesseract.js 中文/英文模型。文字和坐标只在浏览器本机处理，图片不会上传。首次使用会下载识别模型，之后由浏览器缓存。支持最多 20 张图片，单张上限 18 MB；长图、拍照倾斜、低清晰度和复杂卡片布局可能产生错字，必须在校对页检查周次、星期、节次、课程和地点。

## 提醒边界

- Apple 日历：导出 `.ics`，事件含稳定编号、北京时间、地点和 `VALARM`。课表变更后需重新导出并移除旧日历。
- Web Push：仅在添加到主屏幕的 Web App 中申请权限。客户端以 AES-GCM 加密标题、地点和深链；服务端只保存匿名订阅、触发时间、密文和设备令牌。
- PWA 无法提供原生 iPhone 桌面组件、持续闹铃、可靠后台音频或原生锁屏计时控制。

匿名推送服务位于 `../push-server/`，部署后在设置中填写 HTTPS 地址和 VAPID 公钥。静态 GitHub Pages 无法承担定时推送。

## 本地开发与部署

```powershell
cd web
npm ci
npm test
npm run build
npm run preview -- --port 4173
node browser-check.mjs
```

Node.js 22 或更新版本。`.github/workflows/web-pages.yml` 会在 `main` 分支自动测试、构建并部署 GitHub Pages。仓库 Settings → Pages 必须选择 **GitHub Actions**。

`vite.config.ts` 的 `base`、Manifest `id/start_url/scope` 固定为 `/CampusSchedule/`。更换域名或路径会形成新的浏览器存储空间。

## 数据结构

- `src/domain.ts`：课程系列、重复任务、笔记、专注、成就、个人资料和日期派生。
- `src/store.ts`：IndexedDB v2 分仓储、旧版迁移、草稿、图片 Blob 和完整备份。
- `src/image-import.ts`：浏览器 OCR 与课表坐标重建。
- `src/ics.ts`：Apple 日历导出。
- `src/push.ts`、`public/push-sw.js`：匿名订阅、浏览器端加密和通知深链。
- `src/model.ts`、`word.ts`、`pdf.ts`：原有统一课表解析器。

## 隐私与验证状态

课表、任务、笔记、头像、校园资料、专注记录和成就默认只存本机。无账号、广告、分析或社区。推送服务不接收明文学习内容。

Windows 已验证生产构建、29 项/17 周 PDF 导入、待办保存重开、Service Worker 离线重开和 320–1280 px 响应式布局。iPhone 的添加到主屏幕、Safari 快捷指令、图片 OCR 内存、Web Push 与 Apple 日历仍需真机验收，因此当前版本标记为 beta，不作为 1.0 正式版。
