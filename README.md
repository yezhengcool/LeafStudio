# LeafStudio 激活系统

<div align="center">

🍃 **基于数据库的设备激活管理系统**

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://www.android.com)
[![Database](https://img.shields.io/badge/database-PostgreSQL-blue.svg)](https://www.postgresql.org)

</div>

---

## 📖 简介

LeafStudio 激活系统是一个完整的设备激活管理解决方案，包含：

- 🔐 **服务器端验证** - 基于数据库的安全验证
- ⏰ **网络时间校准** - 防止本地时间篡改
- 🎫 **一码一用机制** - 每个激活码只能使用一次
- 🌐 **Web 管理后台** - 现代化的设备管理界面
- 📊 **实时统计分析** - 设备和激活码使用情况
- 📱 **Android 客户端** - 无缝集成的激活功能

---

## ✨ 特性

### 安全性
- ✅ 服务器端验证，防止破解
- ✅ 网络时间校准，防止时间篡改
- ✅ HTTPS 加密通信
- ✅ 激活码与设备绑定

### 管理功能
- ✅ Web 管理后台
- ✅ 批量生成激活码
- ✅ 延长设备激活时间
- ✅ 查看设备激活状态
- ✅ 实时统计分析

### 用户体验
- ✅ 简单的激活流程
- ✅ 实时剩余时间显示
- ✅ 友好的错误提示
- ✅ 离线缓存支持

---

## 🚀 快速开始

### 5 分钟快速部署

1. **初始化数据库**
   ```sql
   -- 在 Neon Console 执行
   CREATE TABLE activation_records (...);
   CREATE TABLE activation_codes (...);
   ```

2. **部署 Web 管理后台**
   ```bash
   # 上传到 https://yezheng.dpdns.org/tv/
   cp web_admin/* /var/www/html/tv/
   ```

3. **部署 API 服务**
   ```bash
   # 上传到 https://yezheng.dpdns.org/tv/api/
   cp server/* /var/www/html/tv/api/
   # 配置 Neon API Key
   vim /var/www/html/tv/api/config.php
   ```

4. **生成激活码**
   - 访问 https://yezheng.dpdns.org/tv/
   - 点击"激活码管理" → "生成激活码"

5. **测试激活**
   - 打开 Android APP
   - 输入激活码
   - 验证激活成功

详细步骤请查看 [快速开始指南](docs/QUICK_START.md)

---

## 📁 项目结构

```
LeafStudio/
├── app/                          # Android 客户端
│   └── src/main/java/com/leafstudio/tvplayer/
│       ├── utils/
│       │   └── ActivationManager.kt
│       └── PlaybackActivity.kt
│
├── web_admin/                    # Web 管理后台
│   ├── index.html
│   ├── style.css
│   ├── script.js
│   └── README.md
│
├── server/                       # PHP API 服务
│   ├── config.php
│   ├── index.php
│   ├── api/
│   │   ├── time.php
│   │   ├── activate.php
│   │   ├── check.php
│   │   └── admin/
│   └── database/
│       └── init.sql
│
├── docs/                         # 文档
│   ├── QUICK_START.md           # 快速开始
│   ├── DEPLOYMENT.md            # 部署指南
│   ├── ACTIVATION_API.md        # API 文档
│   ├── ACTIVATION_UPGRADE.md    # 升级说明
│   └── SUMMARY.md               # 系统总结
│
└── tools/                        # 工具
    └── generate_activation_codes.py
```

---

## 🌐 在线演示

- **Web 管理后台**: https://yezheng.dpdns.org/tv/
- **API 服务**: https://yezheng.dpdns.org/tv/api/

---

## 📚 文档

| 文档 | 说明 |
|------|------|
| [快速开始](docs/QUICK_START.md) | 5 分钟快速部署指南 |
| [部署指南](docs/DEPLOYMENT.md) | 完整的部署步骤和配置 |
| [API 文档](docs/ACTIVATION_API.md) | API 接口详细说明 |
| [升级说明](docs/ACTIVATION_UPGRADE.md) | 从本地验证升级到数据库验证 |
| [系统总结](docs/SUMMARY.md) | 完整的系统实现总结 |
| [Web 后台说明](web_admin/README.md) | Web 管理后台使用指南 |

---

## 🔧 技术栈

### Android 客户端
- Kotlin
- OkHttp
- SharedPreferences

### Web 管理后台
- HTML5
- CSS3
- JavaScript (ES6+)
- Fetch API

### API 服务
- PHP 7.4+
- cURL

### 数据库
- PostgreSQL (Neon Database)

---

## 📊 系统架构

```
┌─────────────┐
│ Android APP │
└──────┬──────┘
       │ HTTPS
       ▼
┌─────────────┐     ┌──────────────┐
│  PHP API    │────▶│ Neon Database│
└──────┬──────┘     └──────────────┘
       ▲
       │ HTTPS
┌──────┴──────┐
│ Web 管理后台 │
└─────────────┘
```

---

## 🔐 安全特性

1. **服务器端验证** - 所有激活验证在服务器端完成
2. **网络时间** - 使用服务器时间，防止本地时间篡改
3. **一码一用** - 每个激活码只能使用一次
4. **设备绑定** - 激活码与设备机器码绑定
5. **HTTPS 加密** - 所有通信使用 HTTPS
6. **参数验证** - 严格的输入参数验证

---

## 📱 Android 客户端集成

```kotlin
// 验证激活码
ActivationManager.validateActivationCodeFromServer(
    context,
    activationCode
) { info ->
    if (info != null && info.isValid) {
        Toast.makeText(context, "激活成功！", Toast.LENGTH_LONG).show()
    }
}

// 检查激活状态
ActivationManager.checkActivationStatus(context) { info ->
    if (info != null && info.isValid) {
        // 已激活
    }
}

// 获取剩余时间
val remaining = ActivationManager.getRemainingTime(context)
val formattedTime = ActivationManager.formatRemainingTime(remaining)
```

---

## 🌟 Web 管理后台功能

### 设备管理
- 查看所有设备
- 搜索和筛选
- 延长激活时间
- 删除设备
- 查看设备详情

### 激活码管理
- 批量生成激活码
- 查看使用状态
- 复制激活码
- 删除未使用的激活码

### 统计分析
- 设备总数
- 已激活设备
- 即将过期设备
- 已过期设备
- 激活码使用情况

---

## 🎯 使用场景

### 场景 1：新用户激活
1. 用户下载 APP
2. 复制机器码发送给管理员
3. 管理员生成激活码
4. 用户输入激活码完成激活

### 场景 2：延长激活时间
1. 管理员登录 Web 后台
2. 找到即将过期的设备
3. 点击"延长"按钮
4. 输入延长天数
5. 确认延长

### 场景 3：批量生成激活码
1. 管理员登录 Web 后台
2. 点击"生成激活码"
3. 输入数量和有效期
4. 批量生成并分发

---

## 🛠️ 开发

### 环境要求
- Android Studio 4.0+
- PHP 7.4+
- PostgreSQL (Neon Database)
- Nginx/Apache

### 本地开发

1. **克隆项目**
   ```bash
   git clone https://github.com/yourusername/LeafStudio.git
   cd LeafStudio
   ```

2. **配置数据库**
   - 在 Neon Console 创建数据库
   - 执行 `server/database/init.sql`

3. **配置 API**
   - 编辑 `server/config.php`
   - 填入 Neon API Key

4. **运行 Web 后台**
   ```bash
   cd web_admin
   python3 -m http.server 8000
   # 访问 http://localhost:8000
   ```

---

## 🤝 贡献

欢迎贡献代码、报告问题或提出建议！

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

---

## 📞 联系方式

- **项目主页**: https://github.com/yezhengcool/LeafStudio
- **问题反馈**: https://github.com/yezhengcool/LeafStudio/issues
- **邮箱**: your.email@example.com

---

## 🙏 致谢

感谢以下开源项目：

- [Neon Database](https://neon.tech) - PostgreSQL 数据库托管
- [OkHttp](https://square.github.io/okhttp/) - HTTP 客户端
- [ExoPlayer](https://exoplayer.dev) - 媒体播放器

---

## 📈 更新日志

### v2.0.0 (2025-11-26)
- ✅ 升级为基于数据库的验证方式
- ✅ 添加 Web 管理后台
- ✅ 添加网络时间校准
- ✅ 添加一码一用机制
- ✅ 添加实时统计分析

### v1.0.0 (2025-11-20)
- ✅ 初始版本
- ✅ 本地激活码验证

---

<div align="center">

**Made with ❤️ by LeafStudio Team**

[⬆ 回到顶部](#leafstudio-激活系统)

</div>
