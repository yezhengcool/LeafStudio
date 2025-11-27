# 🚀 5分钟快速开始

## 📋 准备工作

您需要：
- ✅ Neon 数据库账户
- ✅ 服务器 SSH 访问权限（https://yezheng.dpdns.org/tv/）

---

## ⚡ 3步完成部署

### 步骤 1：初始化数据库（2分钟）

1. 访问：https://console.neon.tech/app/projects/735ebded-3f71-456c-adaf-04564e219725/branches/main/query

2. 复制并粘贴以下 SQL：

```sql
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

CREATE INDEX IF NOT EXISTS idx_machine_code ON activation_records(machine_code);

CREATE TABLE IF NOT EXISTS activation_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    duration_days INTEGER NOT NULL,
    is_used BOOLEAN DEFAULT FALSE,
    used_by_machine VARCHAR(16) DEFAULT NULL,
    used_at BIGINT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_code ON activation_codes(code);

CREATE TABLE IF NOT EXISTS admin_users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP DEFAULT NULL
);

INSERT INTO admin_users (username, password_hash) 
VALUES ('LeafStudio', MD5('Test23456'))
ON CONFLICT (username) DO NOTHING;
```

3. 点击 **Run** 按钮

---

### 步骤 2：上传文件（2分钟）

```bash
cd web_admin_static

# 上传到服务器
scp login.html user@server:/var/www/html/tv/
scp index.html user@server:/var/www/html/tv/
scp style.css user@server:/var/www/html/tv/
scp script.js user@server:/var/www/html/tv/
```

---

### 步骤 3：访问并登录（1分钟）

1. 访问：https://yezheng.dpdns.org/tv/login.html

2. 登录：
   - 用户名：`LeafStudio`
   - 密码：`Test23456`

3. **立即修改密码**！

---

## ✅ 完成！

现在您可以：

- ✅ 查看所有设备
- ✅ 管理激活码
- ✅ 延长激活时间
- ✅ 添加设备备注
- ✅ 查看统计数据

---

## 📚 详细文档

- **数据库初始化**: `DATABASE_INIT.md`
- **部署指南**: `DEPLOY_TO_SERVER.md`
- **功能说明**: `web_admin_static/README.md`

---

## 🎯 默认账户

```
用户名: LeafStudio
密码: Test23456
```

**⚠️ 首次登录后请立即修改密码！**

---

就这么简单！🎉
