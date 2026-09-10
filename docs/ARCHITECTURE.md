# 项目架构

## 设计目标

中大课表助手把学校导出的复杂课表转换成统一课程模型，再由同一份数据驱动应用页面、桌面组件和提醒。解析器不依赖界面，便于针对真实导出格式测试，也避免不同展示入口出现周次或时间计算差异。

## 模块

```text
CampusSchedule
├── core                         纯 Kotlin/JVM 业务模块
│   ├── WordScheduleParser       Word XML / DOCX 表格解析
│   ├── PdfScheduleParser        PDF 字形坐标与边框重建
│   ├── WebScheduleParser        网页表格跨度和星期节次映射
│   ├── ScheduleAssembler        周次、节次与候选安排合并
│   └── ScheduleEngine           日期展开、冲突、差异与单次修改
└── app                          Android 应用模块
    ├── SchoolBrowserActivity    学校网页及按需导入
    ├── SchoolImportCache        规范化课程的一次性私有回传
    ├── ScheduleFileImporter     文件识别和受限读取
    ├── Room Store               本地应用状态
    ├── Compose UI               今日、计划、专注、复盘、成就、设置、个人中心与 DIY
    ├── Glance Widgets           下一节课、今日课程、学习看板
    ├── ReminderScheduler        课程与任务提醒计划和恢复
    ├── FocusRuntime             专注状态、闹钟、锁屏通知与环境音服务
    ├── DiyRenderer              图片裁剪、模糊、遮罩和组件背景
    ├── PersonalizationStore     头像、资料、主题和启动设置
    └── EntryActivity            长按充能启动与动态氛围场景
```

## 导入流程

1. 用户通过系统文件选择器选择文件；应用限制文件大小，不只依赖扩展名判断内容。
2. Word 解析器读取 Flat OPC Word XML 或 DOCX，依据表头和合并单元格还原星期与节次。
3. PDF 适配层提取文字坐标和绘制边框，核心解析器据此重建单元格和跨页列。
4. `ScheduleAssembler` 将课程、周次、教师、地点和节次整理成统一规则；同课同时间的多名教师或教室保留为候选。
5. 用户核对导入预览后才写入 Room；解析或保存失败不覆盖旧课表。

## 课程与提醒

课程规则存储星期、有效周次、起止节次和候选安排。`ScheduleEngine` 使用第一教学周周一和北京时间展开真实日期，并叠加单次取消、改时间和改地点；`ManualLesson` 保存用户指定日期的一次课程。导入课程和自建课程统一输出 `Occurrence`，提醒、页面和组件消费同一结果。

`AlarmManager` 优先申请准时提醒能力；权限不足时使用系统允许的降级调度并提示可能延迟。设备重启、系统时间或时区变化、应用升级和课表修改都会重建课程提醒。持续闹铃由前台媒体播放服务承担，用户停止或 60 秒后结束。

`StudyTask` 保存任务类型、课程规则关联、课程名快照、截止时间、优先级、备注、单次提醒和完成时间。`StudyTaskEngine` 负责校验、排序、提醒时间计算和课程失联判断。任务与课表共享 `AppData` 的兼容序列化容器，但删除或重新导入课程不会级联删除任务。

0.7.0 在 `StudyTask` 中加入子任务定义、重复规则和稀疏实例状态。`TaskOccurrence` 由引擎按日期范围动态展开；只有完成、单次修改或单次删除会写入实例状态，因此长期重复任务不会无限扩张数据库。页面、月历、组件和提醒均消费该实例模型。

任务提醒使用独立通知渠道、AlarmManager action、requestCode 和已投递令牌。编辑任务会重建调度，完成或删除会取消通知；通知动作可直接查看或完成任务。重启、系统时间和时区变化沿用统一恢复入口，只安排未完成且尚未过期的提醒。

## 桌面 DIY

下一节课、今日课程和学习看板三个组件分别保存 `DiyStyle`，内容包含样式、图片引用、裁剪位置、缩放、模糊、遮罩、透明度、标题、短句和字体比例。图片经系统选择器读取、校正方向、限制尺寸并重新编码到应用私有目录，不保存来源 URI 或 EXIF 信息。学习看板读取任务实例并按尺寸显示 2／3／4 项，同时使用独立的 `study` DIY 存储键。

编辑器和 Glance 共用 `DiyRenderer`，确保背景裁剪与样式规则一致。组件内容仍来自课程模型，DIY 不改变地点候选、提醒或冲突语义。

## 数据边界

- Room 保存当前课表、单次自建课程、课程颜色、课业待办、个人修改和提醒设置；SharedPreferences 保存 DIY 外观、主题收藏和个人中心设置。
- NetID、学号、宿舍和头像与课表状态分开，不参与导入、导出、网页登录或排查信息。头像与自选背景使用私有文件名，不保留来源 URI 或 EXIF。
- 应用备份关闭；卸载或清除数据会删除本机状态。
- 0.4.0 声明网络权限，学校登录 Activity 仅供应用内部启动。顶层导航限定学校 HTTPS 域名，学校页面引用的认证资源正常加载；证书错误取消访问，不放宽混合内容。
- 私有课表测试样本位于被 `.gitignore` 排除的 `private-fixtures`，不进入源码包。

## 当前工程取舍

- 选择纵向周列表而不是七列网格，以适应手机窄屏和较大字号。
- 用户自行在真实学校网页登录，仅在准确课表路由且全部周次已选中时执行导入脚本；不自动登录或读取登录表单。
- 首版只维护一份当前学期课表，不增加账号、云同步或数据迁移。
- 华为原生鸿蒙卡片属于另一套平台能力，不由安卓 Glance 组件覆盖。

## 个性化与启动 · 0.5.0

应用图标入口由 `EntryActivity` 承担，长按约 1.5 秒完成后显式打开 `MainActivity`；组件、通知和闹铃仍直接打开主页面。系统关闭动画或用户关闭充能时降低或跳过效果。三套内置启动场景由 Compose Canvas 绘制并使用轻量动画，自选背景读取私有压缩图。

`PersonalizationStore` 保存 `ProfileData`、`ThemeSettings` 语义和 `LaunchStyle`。组件同步读取主题与头像，头像由 `AvatarRenderer` 生成带透明圆角的受限位图。普通 Glance/RemoteViews 组件不播放视频、GIF 或实况照片，`DiyRenderer` 只输出三套场景的静态海报。

## 网页导入 · 0.4.0

原生按钮启动一次导入任务：获取脱敏表格快照并检查稳定状态，临时捕获学校自身导出（等待最多 8 秒，8 MB 限制，分块回传 Base64）；获取失败时使用同次表格快照还原 rowspan/colspan。结束时移除临时捕获，导航或返回取消正在执行的读取；导入完成前再次核对页面和快照未变化。

解析输出统一 ImportResult，SchoolImportCache 缓存规范化结果，以随机 UUID 令牌回传 MainViewModel，消费后删除，再复用 preview/confirmImport。课表数据库结构不变。

方向变化保留当前网页；Activity 被系统重新创建时重新打开学校登录页，保留 Cookie，放弃未完成的读取。不持久化网页 Bundle，避免保存表单内容。真实登录与二次验证仍需用户真机验收。


## 0.4.2 · 手机版课表导入

0.4.2 当前网页导入不再调用学校导出，改为完整课表导航、全部周次选择、稳定快照读取和本地解析。之前的导出脚本保留但不在生产导入路径调用。

## Web / PWA

`web/README.md` 描述浏览器端结构。Web 为独立 Vite/React/TypeScript 工程，移植 core 课程规则，PDF.js 读取几何信息，IndexedDB 存储。共享的是模型语义与回归样本，不运行 JVM/Room。Service Worker 仅缓存本站构建资源，不代理或缓存学校登录；版本更新不删除课程。Safari 快捷指令通过显式剪贴板操作转移经验证的 WebImportPayload。

## 学习日历 · 0.7.0

`LearningCalendar` 组合既有课程 `Occurrence` 与任务 `TaskOccurrence`，月视图只展开当前月份，待办列表只展开有限窗口，提醒只计算每个系列的下一次有效实例。任务通知、桌面快捷完成和深链同时携带 `taskId` 与 `occurrenceKey`，确保重复系列中的单次操作互不覆盖。

## 专注、复盘与成就 · 0.8.0

`ActiveFocusState` 保存当前计时模式、墙上时间、单调时钟基线、累计秒数、关联课程或任务和暂停状态。`FocusEngine` 是计时计算的纯 Kotlin 来源；`FocusRuntime` 负责 AlarmManager、锁屏通知和媒体前台服务，应用页面与学习看板只读取同一状态。完成后写入不可变 `FocusSession`，复盘忽略休息记录并按北京时间周界限聚合。

`AchievementEngine` 以专注记录及唯一任务实例完成证据计算进度。五个内置定义由代码提供，自定义 `AchievementDefinition`、`BadgeDesign` 与 `AchievementProgress` 作为带默认值字段保存在 `AppData` JSON 中。解锁状态只向前推进；庆祝动画是否看过独立记录，不影响条件。自选徽章图片走现有受限图片导入链路并存入私有目录。

Room 数据库版本保持 1，0.8.0 只扩展序列化容器的可选默认字段，因此旧版记录可以直接解码。正在专注、设置、记录、成就和精选编号与课表处于同一个原子本地状态中；重新导入课表不会清除这些字段。

## 0.9.0：自建课程系列

`ManualLesson` 保留基础课程并增加 `repeatCount`、`ManualLessonRevision` 与 `ManualLessonInstanceEdit`。`ScheduleEngine` 按“基础 → 本次及以后修订 → 单次例外”展开 `Occurrence`，稳定键为 `manual@课程编号@次数索引`。页面、冲突检测、组件和提醒继续消费同一结果，Room 版本不变，旧 JSON 的默认次数为 1。

启动背景使用三张 1080×2400 离线 WebP。Compose 只解码当前场景并叠加轻量动画；Glance 静态海报由同一资源缩放生成。


## 0.10.0：空档雷达与交互状态

`GapRadarEngine` 只接收本机课程实例、任务实例和当前北京时间。它将当天课程扩展为前后各 10 分钟的占用区间，扫描当前时刻至 23:00 的空档，再按逾期、今日截止、紧急、截止时间和优先级筛选能够完整放入的任务。`StudyTask.estimatedMinutes` 可选且限制为 10–180 分钟，旧任务按 30 分钟参与推荐。确认推荐后复用 `FocusEngine` 启动关联倒计时，不创建第二套计时状态。

主页面用可保存页面容器保留滚动与表单状态，并维护访问顺序。弹窗与全屏专注先消费返回事件；首页使用两秒双击退出保护。启动 `LaunchStyle` 与个人中心 `profileBannerStyle` 都增加默认字段，旧 JSON 可直接解码，Room 版本不变。
