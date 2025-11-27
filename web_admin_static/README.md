# 🎉 纯静态部署指南

## ✅ 系统已完成！

**完全静态的激活管理系统**
- 无需 PHP
- 无需 Node.js  
- 无需后端服务器
- 直接连接 Neon 数据库

---

## 📁 文件结构

```
web_admin_static/
├── login.html          # 登录页面 ✅
├── index.html          # 管理后台主页 ✅
├── style.css           # 样式文件 ✅
└── script.js           # 业务逻辑 ✅
```

---

## 🚀 部署步骤（3步）

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

### 第2步：部署到 GitHub Pages

```bash
# 1. 提交代码到 GitHub
git add .
git commit -m "Add static admin panel"
git push origin main

# 2. 在 GitHub 仓库设置中启用 Pages
# Settings → Pages → Source: main branch → /web_admin_static

# 3. 完成！访问：
# https://your-username.github.io/LeafStudio/web_admin_static/
```

### 第3步：测试登录

访问部署的网址，使用默认账户：
- 用户名：`LeafStudio`
- 密码：`Test23456`

---

## 🌐 其他部署方式

### 方法 1: Vercel（推荐）

1. 访问 https://vercel.com
2. 导入 GitHub 仓库
3. Root Directory: `web_admin_static`
4. 点击 Deploy
5. 完成！

### 方法 2: Netlify

1. 访问 https://netlify.com
2. 拖放 `web_admin_static` 文件夹
3. 完成！

### 方法 3: 本地测试

```bash
# 使用 Python 启动本地服务器
cd web_admin_static
python3 -m http.server 8000

# 访问 http://localhost:8000/login.html
```

---

## ⚙️ 配置说明

### 数据库连接

在 `script.js` 和 `login.html` 中已配置：

```javascript
const DATABASE_URL = 'postgresql://neondb_owner:npg_kf5BO3mHDoTZ@ep-sparkling-river-ah52my74-pooler.c-3.us-east-1.aws.neon.tech/neondb?sslmode=require';
```

**⚠️ 安全提示**：
- 数据库密码会暴露在前端代码中
- 建议使用 Neon 的 Row Level Security (RLS)
- 或创建只读/受限权限的数据库用户

---

## 🎯 功能清单

### ✅ 已实现功能

1. **用户登录**
   - 数据库验证
   - Session 管理
   - 密码显示/隐藏

2. **设备管理**
   - 查看所有设备
   - 搜索设备
   - 筛选设备（已激活/即将过期/已过期）
   - 延长激活时间
   - 删除设备
   - 查看设备详情

3. **设备备注**
   - 添加/编辑备注
   - 标识设备所有者

4. **激活码管理**
   - 批量生成激活码
   - 查看激活码列表
   - 复制激活码
   - 删除未使用的激活码

5. **统计分析**
   - 设备总数
   - 已激活设备
   - 即将过期设备
   - 已过期设备
   - 激活码统计

6. **系统设置**
   - 查看账户信息
   - 修改密码
   - 退出登录

---

## 🧪 测试步骤

### 1. 测试登录

```
1. 打开 login.html
2. 输入：LeafStudio / Test23456
3. 应该成功登录并跳转到 index.html
```

### 2. 测试设备管理

```
1. 点击"设备管理"
2. 应该显示设备列表（可能为空）
3. 测试搜索、筛选功能
```

### 3. 测试激活码生成

```
1. 点击"激活码管理"
2. 点击"生成激活码"
3. 输入数量和天数
4. 点击"生成"
5. 应该看到新生成的激活码
```

### 4. 测试修改密码

```
1. 点击"系统设置"
2. 输入当前密码和新密码
3. 点击"修改密码"
4. 退出登录
5. 使用新密码登录
```

---

## 🔒 安全建议

### 1. 使用环境变量（Vercel/Netlify）

在部署平台设置环境变量：

```
DATABASE_URL=postgresql://...
```

然后在代码中使用：

```javascript
const DATABASE_URL = process.env.DATABASE_URL;
```

### 2. 配置 Neon Row Level Security

```sql
-- 启用 RLS
ALTER TABLE admin_users ENABLE ROW LEVEL SECURITY;

-- 创建策略
CREATE POLICY admin_policy ON admin_users
FOR ALL
USING (true);
```

### 3. 定期更换密码

首次登录后立即修改默认密码。

---

## 📊 性能优化

### 1. 缓存策略

浏览器会自动缓存静态文件（HTML/CSS/JS）。

### 2. CDN 加速

GitHub Pages 和 Vercel 自动提供 CDN。

### 3. 数据库连接池

Neon 自动管理连接池（使用 pooler 地址）。

---

## 🐛 故障排查

### 问题 1: 无法连接数据库

**检查**：
1. 数据库连接字符串是否正确
2. 网络是否正常
3. 浏览器 Console 是否有错误

### 问题 2: 登录失败

**检查**：
1. 数据库是否已初始化
2. admin_users 表是否存在
3. 默认账户是否已创建

### 问题 3: CORS 错误

**解决**：
- 使用 HTTPS 访问
- 或使用本地服务器（不要直接打开 file://）

---

## 📝 默认账户

```
用户名: LeafStudio
密码: Test23456
```

**⚠️ 首次登录后请立即修改密码！**

---

## 🎉 完成！

您的纯静态激活管理系统已经准备就绪！

**特点**：
- ✅ 完全免费
- ✅ 无需服务器
- ✅ 自动 HTTPS
- ✅ 全球 CDN
- ✅ 易于维护

享受您的管理系统吧！🚀
