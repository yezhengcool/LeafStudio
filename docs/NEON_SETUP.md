# Neon 数据库配置指南

## 📋 您的数据库信息

**项目 ID**: `735ebded-3f71-456c-adaf-04564e219725`

**REST API URL**: 
```
https://ep-sparkling-river-ah52my74.apirest.c-3.us-east-1.aws.neon.tech/neondb/rest/v1
```

**PostgreSQL 连接字符串** (用于直接连接):
```
postgresql://neondb_owner:npg_kf5BO3mHDoTZ@ep-sparkling-river-ah52my74-pooler.c-3.us-east-1.aws.neon.tech/neondb?sslmode=require
```

---

## 🔑 获取 API Key

### 步骤 1: 访问 Neon Console

打开浏览器访问：https://console.neon.tech

### 步骤 2: 选择项目

找到并点击项目：`735ebded-3f71-456c-adaf-04564e219725`

### 步骤 3: 进入 API Keys 设置

1. 点击左侧菜单 **Settings**
2. 点击 **API Keys** 标签

### 步骤 4: 生成 API Key

1. 点击 **Generate new API key** 按钮
2. 输入描述（如：`LeafStudio Activation System`）
3. 点击 **Generate**
4. **立即复制 API Key**（只显示一次！）

### 步骤 5: 更新配置文件

编辑 `server/config.php`，将 API Key 替换：

```php
define('NEON_API_KEY', '你复制的API Key');
```

---

## 🗄️ 初始化数据库

### 方法 1: 使用 Neon Console SQL Editor

1. 在 Neon Console 中点击 **SQL Editor**
2. 复制并执行以下 SQL：

```sql
-- 创建激活记录表
CREATE TABLE IF NOT EXISTS activation_records (
    id BIGSERIAL PRIMARY KEY,
    machine_code VARCHAR(16) NOT NULL UNIQUE,
    activation_code VARCHAR(50) NOT NULL,
    activation_time BIGINT NOT NULL,
    expiry_time BIGINT NOT NULL,
    device_note VARCHAR(200) DEFAULT NULL,
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

-- 创建管理员账户表
CREATE TABLE IF NOT EXISTS admin_users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP DEFAULT NULL
);

-- 插入默认管理员账户
-- 用户名: LeafStudio
-- 密码: Test23456
INSERT INTO admin_users (username, password_hash) VALUES
('LeafStudio', MD5('Test23456'))
ON CONFLICT (username) DO NOTHING;
```

### 方法 2: 使用 psql 命令行

```bash
# 使用您的连接字符串
psql "postgresql://neondb_owner:npg_kf5BO3mHDoTZ@ep-sparkling-river-ah52my74-pooler.c-3.us-east-1.aws.neon.tech/neondb?sslmode=require"

# 然后执行 SQL 文件
\i server/database/init.sql
\i server/database/create_admin_table.sql
```

---

## ✅ 验证数据库

执行以下 SQL 验证表已创建：

```sql
-- 查看所有表
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public';

-- 应该看到:
-- activation_records
-- activation_codes
-- admin_users

-- 查看管理员账户
SELECT id, username, created_at 
FROM admin_users;

-- 应该看到:
-- id | username    | created_at
-- 1  | LeafStudio  | 2025-11-26 ...
```

---

## 🚀 完整部署清单

### ✅ 第一步：数据库配置

- [ ] 获取 Neon API Key
- [ ] 更新 `server/config.php` 中的 API Key
- [ ] 在 Neon Console 执行初始化 SQL
- [ ] 验证表已创建
- [ ] 验证默认管理员账户已创建

### ✅ 第二步：上传文件

- [ ] 上传整个 `server/` 文件夹到 `/var/www/html/tv/api/`
- [ ] 上传 `web_admin/` 的 4 个文件到 `/var/www/html/tv/`

### ✅ 第三步：测试

- [ ] 测试 API: `curl https://yezheng.dpdns.org/tv/api/time`
- [ ] 访问登录页: `https://yezheng.dpdns.org/tv/login.html`
- [ ] 使用默认账户登录: `LeafStudio` / `Test23456`
- [ ] 测试修改密码功能
- [ ] 测试设备管理功能

---

## 🔧 故障排查

### 问题 1: API 返回 "Neon API Error"

**原因**: API Key 无效或未设置

**解决**:
1. 检查 `config.php` 中的 `NEON_API_KEY`
2. 确保 API Key 正确复制（没有多余空格）
3. 在 Neon Console 检查 API Key 是否有效

### 问题 2: 表不存在

**原因**: 数据库未初始化

**解决**:
1. 在 Neon Console SQL Editor 执行初始化 SQL
2. 或使用 psql 命令行执行 SQL 文件

### 问题 3: 登录失败

**原因**: admin_users 表未创建或默认账户未插入

**解决**:
```sql
-- 检查表是否存在
SELECT * FROM admin_users;

-- 如果表不存在，执行:
CREATE TABLE admin_users (...);

-- 如果没有默认账户，执行:
INSERT INTO admin_users (username, password_hash) VALUES
('LeafStudio', MD5('Test23456'));
```

---

## 📝 快速测试命令

```bash
# 1. 测试 API 连接
curl https://yezheng.dpdns.org/tv/api/time

# 2. 测试登录 API
curl -X POST https://yezheng.dpdns.org/tv/api/admin/login \
  -H "Content-Type: application/json" \
  -d '{"username":"LeafStudio","password":"Test23456"}'

# 3. 测试激活码生成
curl -X POST https://yezheng.dpdns.org/tv/api/admin/generate \
  -H "Content-Type: application/json" \
  -d '{"count":5,"duration_days":30}'
```

---

## 🎯 重要提示

1. **API Key 安全**
   - 不要将 API Key 提交到 Git
   - 不要在前端代码中暴露 API Key
   - 定期更换 API Key

2. **密码安全**
   - 首次登录后立即修改密码
   - 使用强密码
   - 定期更换密码

3. **HTTPS**
   - 必须使用 HTTPS
   - 不要在 HTTP 下使用

4. **备份**
   - 定期备份数据库
   - 导出重要数据

---

## 📞 需要帮助？

如果遇到问题：

1. 检查 PHP 错误日志
2. 检查浏览器开发者工具 Console
3. 检查 Network 标签查看 API 请求
4. 参考 `docs/DATABASE_LOGIN.md`

---

祝您部署顺利！🎉
