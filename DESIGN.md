---
version: alpha
colors:
  primary: "#176B52"
  background: "#F3F7F4"
  foreground: "#172D26"
  darkBackground: "#14251E"
  darkPrimary: "#91D6B4"
  darkForeground: "#E2F2E9"
  container: "#D8EBE1"
  navigation: "#EAF1EC"
  darkContainer: "#244C3C"
  darkNavigation: "#1C3026"
typography:
  heading:
    fontFamily: "sans-serif"
    fontSize: "30px"
  body:
    fontFamily: "sans-serif"
    fontSize: "16px"
rounded:
  card: "18px"
  featured: "24px"
spacing:
  screen: "20px"
  card: "16px"
omitted:
  - section: components
    reason: "Native Material 3 components, described below"
---

# 中大课表助手设计规范

## Overview
面向中山大学学生的安卓个人工具，以赶往教室前的一眼阅读为核心。采用课表与教学楼门牌的清晰层级：大号时间、课程名、教室。深绿只强调正在发生和即将发生的课程。中文简体，日期按北京时间计算。本应用不是学校官方产品。

这是产品界面，不使用营销横幅、学校校徽或登录页仿制。根据用户已确认的方案开发，所有私有课表样本仅用于本机测试，不打包到安装包和源码分发。

本文件记录规范，`app/src/main/kotlin/cn/campus/schedule/Theme.kt` 的 Palette 是运行时颜色来源，Compose 和 Glance 共同消费。上述 px 设计数值在安卓中分别映射到 sp（文字）和 dp（尺寸）。

## Colors
浅色主题使用 primary / background / foreground；深色主题对应 darkPrimary / darkBackground / darkForeground。错误和冲突使用 Material 3 error 色，并始终配文字。保留系统权限与学校网页的原生外观。

## Typography
系统中文无衬线字体，无外部字体下载。时间使用 32sp 粗体，页面标题 30sp，正文使用 Material 3 body，说明文字使用 bodySmall。完整课程名和教室允许换行，桌面组件受尺寸限制最多两行，点击查看完整详情。尊重系统字号和屏幕阅读器。

## Layout
主界面三栏底部导航：今日、课表、导入与设置。页面边距 20dp；课程卡片内边距 16dp；列表自然滚动。今日页面先突出下一节课，再显示当天所有安排。每周课表按星期一到星期日纵向排列，避免长课程名在手机网格中难以阅读。日期和时间编辑采用明确格式的原生文本输入，字段显示标签与校验错误。

## Elevation & Depth
平面、细边框卡片；下一节课使用主色调容器区分。使用 Material 3 原生对话框表达临时操作，不叠加装饰阴影。

## Shapes
课程卡片 18dp，重点卡片 24dp，桌面 20dp。按钮采用 Material 3 形状与原生触控反馈。

## Components
统一 LessonCard 用于今日和周列表。无课、未导入、学期结束、已取消、待确认地点、真实冲突都有文字状态。导入忙碌时显示线性进度并禁止再次提交。保存使用同一反馈渠道，解析失败保留原课表。预览取消不产生写入。

所有操作使用 Compose 原生 Button、TextButton、RadioButton、可访问的 Card 等组件；图标来自 Material Icons，独立图标按钮有中文内容描述。仅使用原生状态反馈动画。

## Do's and Don'ts
- 优先显示真实时间、地点、待处理问题。
- 多教室保留候选，并提供本次选择，不替用户猜测。
- 不显示学生姓名、学号、密码或验证码，不把私有样本当作默认课程。
- 不宣称未验证的手机后台提醒或学校登录已经通过。

## PDF import update · 0.1.1
文件入口统一接受 Word / PDF，不新增重复页面。使用同一导入预览、增删改摘要和确认保存操作；解析失败不修改旧数据。明确说明先把聊天文件保存到手机，PDF 扫描件不可导入。

## 多机型适配 · 0.2.0
保持现有绿色 Palette 与系统字体。提醒检查按具体系统状态逐项呈现，不使用“全部安全”总分。品牌通过原生 Material DropdownMenu 切换，文字说明自然换行，设置页由 LazyColumn 统一滚动。署名“xiaoyle 制作”固定于两个组件底部，11sp；用户允许放大组件：下一节课默认 250×180dp，今日课程默认 250×220dp，下限 220×180dp；紧凑布局采用 4dp 外边距，避免大字号挤掉地点；组件正文使用剩余空间，小尺寸先显示时间、课程名和地点。普通通知测试、延时测试和持续闹铃试听分别命名，停止闹铃始终可从设置页操作。学校登录入口本版移除。


## 桌面 DIY · 0.3.0
由用户选择照片作为视觉主体，编辑器沿用 Palette 和 Material 3 系统中文字体。顶部固定布局预览、下部设置滚动、底部固定保存。照片卡使用渐变暗遮罩和白字；毛玻璃对选图做降采样模糊并加浅透明内面板；便签使用 #F5EEDC / #FAF5E5 纸色、#E6EEDC 课程底色、#24352E 正文。点缀色由 DiyStyle 统一定义为薄荷 #B7F2D5、晴蓝 #BEE2FF、莓粉 #FFD1DF、暖杏 #FFE2A8。DiyRenderer 为预览和 Glance 提供同源背景渲染，前景颜色来自 DiyStyle.foreground/highlight。用户可调透明度，需通过预览和手机自行确认与壁纸的对比度。
图片不进入源码或发布包；没有默认人物图。系统字体适应现有应用；字号仅调整课程正文，署名固定 11sp。未保存变化返回时提示；两个组件草稿独立。
