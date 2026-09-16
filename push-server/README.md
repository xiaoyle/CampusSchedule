# 中大课表匿名推送服务

该服务只保存匿名设备令牌、浏览器 PushSubscription、触发时间和浏览器端生成的 AES-GCM 密文。课程名、地点和待办标题不会以明文进入服务器。

## 本地启动

1. 安装 Node.js 22.13 或更新版本。
2. 运行 `npm install`。
3. 运行 `npm run vapid`，将结果填入 `.env.example` 对应环境变量。
4. 提供持久化目录 `DATA_DIR`，否则容器重建会丢失未来提醒。
5. 运行 `npm start`，访问 `/health` 应返回 `ok`。

正式部署必须使用 HTTPS。把公网 HTTPS 地址和 VAPID 公钥填入 PWA 的“设置 → 推送服务配置”，再从 iPhone 主屏幕 Web App 中点击“开启 Web 通知”。

若使用 Docker，应把宿主机目录挂载到 `/data`。GitHub Pages 只托管静态网页，无法替代这个定时发送服务。
