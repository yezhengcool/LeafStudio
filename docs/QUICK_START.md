# 快速开始指南

## 🚀 5 分钟快速部署

### 步骤 1：初始化数据库（2分钟）

1. 访问 [Neon Console](https://console.neon.tech)
2. 打开 SQL Editor
3. 复制并执行以下 SQL：

```sql
CREATE TABLE activation_records (
    id BIGSERIAL PRIMARY KEY,
    machine_code VARCHAR(16) NOT NULL UNIQUE,
    activation_code VARCHAR(50) NOT NULL,
    activation_time BIGINT NOT NULL,
    expiry_time BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_machine_code ON activation_records(machine_code);
CREATE INDEX idx_expiry_time ON activation_records(expiry_time);

CREATE TABLE activation_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    duration_days INTEGER NOT NULL,
    is_used BOOLEAN DEFAULT FALSE,
    used_by_machine VARCHAR(16) DEFAULT NULL,
    used_at BIGINT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_code ON activation_codes(code);
CREATE INDEX idx_is_used ON activation_codes(is_used);
```

### 步骤 2：部署 Web 管理后台（1分钟）

将以下文件上传到 `https://yezheng.dpdns.org/tv/`：

```
web_admin/
├── index.html
├── style.css
└── script.js
```

使用 FTP/SFTP 或者直接在服务器上：

```bash
cd /var/www/html/tv/
# 上传 web_admin 目录下的所有文件
```

### 步骤 3：部署 API 服务（2分钟）

1. 上传 `server/` 目录到服务器

2. 编辑 `config.php`，填入您的 Neon API Key：

```php
define('NEON_API_KEY', 'YOUR_API_KEY_HERE');
```

3. 配置 Nginx（如果使用 Nginx）：

```nginx
location /tv/api/ {
    try_files $uri $uri/ /tv/api/index.php?$query_string;
}
```

4. 测试 API：

```bash
curl https://yezheng.dpdns.org/tv/api/time
```

应该返回：
```json
{
  "status": "success",
  "message": "获取时间成功",
  "data": {
    "timestamp": 1732588479000,
    "datetime": "2025-11-26 09:14:39"
  }
}
```

### 步骤 4：生成测试激活码

1. 访问 `https://yezheng.dpdns.org/tv/`
2. 点击左侧菜单 **激活码管理**
3. 点击 **➕ 生成激活码**
4. 输入数量：10
5. 选择有效期：30天
6. 点击 **开始生成**

### 步骤 5：测试激活流程

1. 打开 Android APP
2. 点击菜单 → 激活
3. 复制机器码
4. 在 Web 后台找到刚生成的激活码
5. 点击 **复制** 按钮
6. 在 APP 中输入激活码
7. 点击 **激活**
8. 验证激活成功！

---

## 📋 文件清单

### 需要上传的文件

#### Web 管理后台
```
/var/www/html/tv/
├── index.html
├── style.css
└── script.js
```

#### API 服务
```
/var/www/html/tv/api/
├── config.php
├── index.php
└── api/
    ├── time.php
    ├── activate.php
    ├── check.php
    └── admin/
        ├── devices.php
        ├── codes.php
        ├── generate.php
        └── extend.php
```

---

## 🔑 获取 Neon API Key

1. 登录 https://console.neon.tech
2. 选择您的项目
3. 点击左侧菜单 **Settings**
4. 点击 **API Keys**
5. 点击 **Generate new API key**
6. 复制 API Key（只显示一次！）
7. 粘贴到 `server/config.php` 中

---

## ✅ 验证部署

### 检查数据库
```sql
-- 查看表是否创建成功
SELECT table_name FROM information_schema.tables 
WHERE table_name IN ('activation_records', 'activation_codes');
```

### 检查 API
```bash
# 测试时间 API
curl https://yezheng.dpdns.org/tv/api/time

# 测试设备列表 API
curl https://yezheng.dpdns.org/tv/api/admin/devices

# 测试激活码列表 API
curl https://yezheng.dpdns.org/tv/api/admin/codes
```

### 检查 Web 后台
1. 访问 https://yezheng.dpdns.org/tv/
2. 应该看到管理后台界面
3. 左侧有导航菜单
4. 右侧显示设备列表（可能为空）

---

## 🐛 常见问题

### Q: API 返回 404
**A:** 检查 Nginx/Apache 配置，确保 URL 重写规则正确。

### Q: 数据库连接失败
**A:** 检查 `config.php` 中的 `NEON_API_KEY` 是否正确。

### Q: Web 后台显示空白
**A:** 打开浏览器开发者工具（F12），查看 Console 是否有错误。

### Q: CORS 错误
**A:** 确保 `config.php` 中包含 CORS 头设置。

---

## 📞 需要帮助？

如果遇到问题，请查看详细文档：

- **API 文档**: `docs/ACTIVATION_API.md`
- **部署指南**: `docs/DEPLOYMENT.md`
- **系统总结**: `docs/SUMMARY.md`

---

## 🎉 完成！

恭喜！您已经成功部署了 LeafStudio 激活系统。

现在您可以：
- ✅ 在 Web 后台管理所有设备
- ✅ 批量生成激活码
- ✅ 延长设备激活时间
- ✅ 查看实时统计数据

享受新的激活系统吧！ 🚀
