# 激活系统部署指南

## 概述

本文档说明如何部署 LeafStudio 激活系统，包括数据库配置、服务器端部署和 Web 管理后台部署。

---

## 系统架构

```
┌─────────────────┐
│  Android 客户端  │
└────────┬────────┘
         │ HTTPS
         ↓
┌─────────────────┐
│   PHP API 服务   │
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  Neon Database  │
│  (PostgreSQL)   │
└─────────────────┘

┌─────────────────┐
│  Web 管理后台    │
└────────┬────────┘
         │ HTTPS
         ↓
┌─────────────────┐
│   PHP API 服务   │
└─────────────────┘
```

---

## 第一步：配置 Neon 数据库

### 1.1 获取数据库连接信息

您的 Neon 数据库 REST API 地址：
```
https://ep-sparkling-river-ah52my74.apirest.c-3.us-east-1.aws.neon.tech/neondb/rest/v1
```

### 1.2 获取 API Key

1. 登录 [Neon Console](https://console.neon.tech)
2. 选择您的项目
3. 进入 **Settings** → **API Keys**
4. 创建新的 API Key 或复制现有的
5. 保存 API Key（只显示一次）

### 1.3 初始化数据库

在 Neon Console 的 SQL Editor 中执行以下 SQL：

```sql
-- 创建激活记录表
CREATE TABLE IF NOT EXISTS activation_records (
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

-- 创建激活码表
CREATE TABLE IF NOT EXISTS activation_codes (
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

或者直接运行提供的脚本：
```bash
psql -h your-neon-host -U your-user -d neondb -f server/database/init.sql
```

---

## 第二步：部署 PHP API 服务

### 2.1 服务器要求

- PHP 7.4 或更高版本
- cURL 扩展
- HTTPS 支持（推荐使用 Let's Encrypt）

### 2.2 上传文件

将 `server/` 目录上传到您的服务器：

```
your-domain.com/tv/api/
├── index.php           # 路由入口
├── config.php          # 配置文件
└── api/
    ├── time.php        # 获取时间
    ├── activate.php    # 激活设备
    ├── check.php       # 检查状态
    └── admin/
        ├── devices.php # 设备列表
        ├── codes.php   # 激活码列表
        ├── generate.php# 生成激活码
        └── extend.php  # 延长激活
```

### 2.3 配置 API

编辑 `server/config.php`：

```php
// Neon 数据库配置
define('NEON_API_URL', 'https://ep-sparkling-river-ah52my74.apirest.c-3.us-east-1.aws.neon.tech/neondb/rest/v1');
define('NEON_API_KEY', 'YOUR_NEON_API_KEY_HERE'); // 替换为您的 API Key
```

### 2.4 配置 Nginx/Apache

#### Nginx 配置示例

```nginx
server {
    listen 443 ssl http2;
    server_name yezheng.dpdns.org;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    root /var/www/html;
    index index.php;
    
    location /tv/api/ {
        try_files $uri $uri/ /tv/api/index.php?$query_string;
    }
    
    location ~ \.php$ {
        fastcgi_pass unix:/var/run/php/php7.4-fpm.sock;
        fastcgi_index index.php;
        include fastcgi_params;
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
    }
}
```

#### Apache 配置示例

在 `server/` 目录创建 `.htaccess`：

```apache
RewriteEngine On
RewriteCond %{REQUEST_FILENAME} !-f
RewriteCond %{REQUEST_FILENAME} !-d
RewriteRule ^(.*)$ index.php [QSA,L]
```

### 2.5 测试 API

访问以下 URL 测试：

```bash
# 测试获取时间
curl https://yezheng.dpdns.org/tv/api/time

# 应该返回：
{
  "status": "success",
  "message": "获取时间成功",
  "data": {
    "timestamp": 1732588479000,
    "datetime": "2025-11-26 09:14:39"
  }
}
```

---

## 第三步：部署 Web 管理后台

### 3.1 上传文件

将 `web_admin/` 目录上传到服务器：

```
your-domain.com/tv/admin/
├── index.html
├── style.css
└── script.js
```

### 3.2 配置 API 地址

编辑 `web_admin/script.js`，确认 API 地址正确：

```javascript
const API_BASE_URL = 'https://yezheng.dpdns.org/tv/api';
```

### 3.3 访问管理后台

打开浏览器访问：
```
https://yezheng.dpdns.org/tv/admin/
```

---

## 第四步：生成初始激活码

### 方法 1：使用 Web 管理后台

1. 访问管理后台
2. 点击左侧菜单 **激活码管理**
3. 点击 **生成激活码** 按钮
4. 输入数量和有效天数
5. 点击 **开始生成**

### 方法 2：直接调用 API

```bash
curl -X POST https://yezheng.dpdns.org/tv/api/admin/generate \
  -H "Content-Type: application/json" \
  -d '{"count": 10, "duration_days": 30}'
```

### 方法 3：直接在数据库插入

在 Neon Console 执行：

```sql
INSERT INTO activation_codes (code, duration_days, is_used) VALUES
('ABCD-EFGH-IJKL', 30, false),
('MNOP-QRST-UVWX', 90, false),
('YZAB-CDEF-GHIJ', 365, false);
```

---

## 第五步：更新 Android 客户端配置

编辑 `app/src/main/java/com/leafstudio/tvplayer/utils/ActivationManager.kt`：

确认 API 地址正确：

```kotlin
private const val API_BASE_URL = "https://yezheng.dpdns.org/tv/api"
private const val API_CHECK_ACTIVATION = "$API_BASE_URL/activation/check"
private const val API_ACTIVATE = "$API_BASE_URL/activation/activate"
private const val API_GET_TIME = "$API_BASE_URL/time"
```

---

## API 接口说明

### 客户端接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/time` | GET | 获取服务器时间 |
| `/api/activation/activate` | GET | 激活设备 |
| `/api/activation/check` | GET | 检查激活状态 |

### 管理后台接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/admin/devices` | GET | 获取设备列表 |
| `/api/admin/codes` | GET | 获取激活码列表 |
| `/api/admin/generate` | POST | 生成激活码 |
| `/api/admin/extend` | POST | 延长激活时间 |

---

## 使用流程

### 用户激活流程

1. 用户打开 APP
2. 点击菜单 → 激活
3. 复制机器码发送给管理员
4. 管理员在后台生成激活码并发送给用户
5. 用户输入激活码点击激活
6. 系统验证成功，显示剩余天数

### 管理员管理流程

1. 登录 Web 管理后台
2. 查看所有设备激活状态
3. 为即将过期的设备延长时间
4. 生成新的激活码
5. 查看统计数据

---

## 安全建议

### 1. 启用 HTTPS

所有 API 必须使用 HTTPS，防止中间人攻击。

### 2. 添加管理员认证

为管理后台添加登录认证：

```php
// 在 api/admin/ 下的文件开头添加
session_start();
if (!isset($_SESSION['admin_logged_in'])) {
    errorResponse('未授权', 401);
}
```

### 3. 限制请求频率

使用 Redis 或文件缓存限制 API 请求频率：

```php
// 简单的频率限制示例
$key = 'rate_limit_' . $_SERVER['REMOTE_ADDR'];
$requests = apcu_fetch($key) ?: 0;

if ($requests > 100) {
    errorResponse('请求过于频繁', 429);
}

apcu_store($key, $requests + 1, 3600);
```

### 4. 保护 API Key

- 不要将 API Key 提交到 Git
- 使用环境变量存储敏感信息
- 定期更换 API Key

### 5. 添加日志记录

记录所有激活尝试：

```php
// 在 activate.php 中添加
file_put_contents(
    'logs/activation.log',
    date('Y-m-d H:i:s') . " - $machineCode - $activationCode\n",
    FILE_APPEND
);
```

---

## 故障排查

### 问题 1：API 返回 500 错误

**原因**：数据库连接失败或 SQL 错误

**解决**：
1. 检查 `config.php` 中的 `NEON_API_KEY` 是否正确
2. 查看 PHP 错误日志：`tail -f /var/log/php-fpm/error.log`
3. 测试数据库连接

### 问题 2：CORS 错误

**原因**：跨域请求被阻止

**解决**：
确保 `config.php` 中包含 CORS 头：
```php
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');
```

### 问题 3：激活码生成失败

**原因**：数据库写入权限不足

**解决**：
检查 Neon 数据库用户权限，确保有 INSERT 权限。

### 问题 4：Web 管理后台无法加载数据

**原因**：API 地址配置错误

**解决**：
1. 打开浏览器开发者工具（F12）
2. 查看 Network 标签中的请求
3. 确认 API 地址是否正确
4. 检查是否有 CORS 错误

---

## 维护建议

### 定期备份数据库

```bash
# 使用 pg_dump 备份
pg_dump -h your-neon-host -U your-user -d neondb > backup.sql
```

### 清理过期数据

定期清理已过期的激活记录：

```sql
-- 删除 1 年前过期的记录
DELETE FROM activation_records 
WHERE expiry_time < EXTRACT(EPOCH FROM NOW() - INTERVAL '1 year') * 1000;
```

### 监控系统状态

- 监控 API 响应时间
- 监控数据库连接数
- 监控磁盘空间使用

---

## 总结

完成以上步骤后，您的激活系统就部署完成了！

**关键文件清单**：
- ✅ 数据库已初始化
- ✅ PHP API 已部署
- ✅ Web 管理后台已部署
- ✅ Android 客户端已配置

**下一步**：
1. 生成测试激活码
2. 使用测试设备验证激活流程
3. 配置管理员认证
4. 启用 HTTPS
5. 添加监控和日志

如有问题，请查看故障排查部分或联系技术支持。
