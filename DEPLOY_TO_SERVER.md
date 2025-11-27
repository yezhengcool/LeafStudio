# 部署到 https://yezheng.dpdns.org/tv/

## 🚀 快速部署（2步）

### 第1步：初始化数据库

访问 Neon Console: https://console.neon.tech

在 SQL Editor 中执行：

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

CREATE INDEX IF NOT EXISTS idx_machine_code ON activation_records(machine_code);

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

CREATE INDEX IF NOT EXISTS idx_code ON activation_codes(code);

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
INSERT INTO admin_users (username, password_hash) VALUES
('LeafStudio', MD5('Test23456'))
ON CONFLICT (username) DO NOTHING;
```

### 第2步：上传文件到服务器

```bash
# 上传 web_admin_static 文件夹的内容到服务器
scp web_admin_static/login.html user@your-server:/var/www/html/tv/
scp web_admin_static/index.html user@your-server:/var/www/html/tv/
scp web_admin_static/style.css user@your-server:/var/www/html/tv/
scp web_admin_static/script.js user@your-server:/var/www/html/tv/

# 或者一次性上传所有文件
scp web_admin_static/*.{html,css,js} user@your-server:/var/www/html/tv/
```

---

## 📁 服务器目录结构

上传后，服务器上应该是这样：

```
/var/www/html/tv/
├── login.html          # 登录页面
├── index.html          # 管理后台
├── style.css           # 样式文件
└── script.js           # 业务逻辑
```

---

## 🌐 访问地址

- **登录页面**: https://yezheng.dpdns.org/tv/login.html
- **管理后台**: https://yezheng.dpdns.org/tv/index.html
- **或直接访问**: https://yezheng.dpdns.org/tv/

---

## 🔑 默认账户

```
用户名: LeafStudio
密码: Test23456
```

---

## ✅ 完成！

就这么简单！

1. ✅ 初始化数据库
2. ✅ 上传 4 个文件
3. ✅ 访问 https://yezheng.dpdns.org/tv/login.html
4. ✅ 登录并开始使用！

---

## 🎯 特点

- ✅ 无需 PHP
- ✅ 无需配置
- ✅ 纯静态文件
- ✅ 直接连接数据库
- ✅ 立即可用

---

## 📝 注意事项

### 1. HTTPS 必须

确保您的服务器支持 HTTPS，因为：
- Neon 数据库需要 SSL 连接
- 浏览器安全策略要求

### 2. 文件权限

确保文件可读：
```bash
chmod 644 /var/www/html/tv/*.html
chmod 644 /var/www/html/tv/*.css
chmod 644 /var/www/html/tv/*.js
```

### 3. 测试连接

上传后，打开浏览器开发者工具（F12），查看 Console 是否有错误。

---

## 🧪 快速测试

```bash
# 1. 上传文件
scp web_admin_static/* user@server:/var/www/html/tv/

# 2. 访问
curl https://yezheng.dpdns.org/tv/login.html

# 3. 应该返回 HTML 内容
```

---

就是这么简单！🎉
