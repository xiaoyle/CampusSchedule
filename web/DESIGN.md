# 网页版设计约定

本工程采用仓库根目录 [DESIGN.md](../DESIGN.md) 的「网页版 / PWA 0.1.0」章节与 [UX-CONTRACT.md](../UX-CONTRACT.md) 的 Web Canonical UI Map。安卓专属 Compose/Glance 约定不套用到浏览器。

运行时颜色与几何来源为 src/style.css；交互来源为 main.tsx 的统一 Modal、课程卡、message 和 run。中文版、本机课表产品；原生 date/select/文件选择器。深绿时间门牌、系统字体、安全区域和底部三项导航。表单失败保留输入；所有导入先预览。系统滚动条保留，文本输入框固定最小高度并内部滚动，不能拖拽破坏布局。
