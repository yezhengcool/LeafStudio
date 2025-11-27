# 📋 数据库初始化详细步骤

## 🎯 目标

在 Neon 数据库中创建 3 个表：
1. `admin_users` - 管理员账户表
2. `activation_records` - 设备激活记录表
3. `activation_codes` - 激活码表

---

## 🚀 初始化步骤（图文教程）

### 第1步：访问 Neon Console

1. 打开浏览器
2. 访问：https://console.neon.tech
3. 登录您的账户

### 第2步：选择项目

1. 在项目列表中找到您的项目
2. 项目 ID：`735ebded-3f71-456c-adaf-04564e219725`
3. 点击进入项目

### 第3步：打开 SQL Editor

1. 在左侧菜单中点击 **"SQL Editor"**
2. 或者直接访问：https://console.neon.tech/app/projects/735ebded-3f71-456c-adaf-04564e219725/branches/main/query

### 第4步：复制并执行 SQL

**复制下面的完整 SQL 代码**：

```sql
-- ========================================
-- LeafStudio 激活系统数据库初始化脚本
-- ========================================

-- 1. 创建激活记录表
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

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_machine_code ON activation_records(machine_code);
CREATE INDEX IF NOT EXISTS idx_expiry_time ON activation_records(expiry_time);

-- 2. 创建激活码表
CREATE TABLE IF NOT EXISTS activation_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    duration_days INTEGER NOT NULL,
    is_used BOOLEAN DEFAULT FALSE,
    used_by_machine VARCHAR(16) DEFAULT NULL,
    used_at BIGINT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_code ON activation_codes(code);
CREATE INDEX IF NOT EXISTS idx_is_used ON activation_codes(is_used);

-- 3. 创建管理员账户表
CREATE TABLE IF NOT EXISTS admin_users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP DEFAULT NULL
);

-- 4. 插入默认管理员账户
-- 用户名: LeafStudio
-- 密码: Test23456
INSERT INTO admin_users (username, password_hash) 
VALUES ('LeafStudio', MD5('Test23456'))
ON CONFLICT (username) DO NOTHING;

-- ========================================
-- 验证安装
-- ========================================

-- 查看所有表
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- 查看管理员账户
SELECT id, username, created_at 
FROM admin_users;
```

### 第5步：粘贴并运行

1. 在 SQL Editor 的输入框中 **粘贴上面的 SQL**
2. 点击 **"Run"** 按钮（或按 Ctrl+Enter / Cmd+Enter）
3. 等待执行完成

### 第6步：验证结果

执行成功后，您应该看到：

**查询结果 1：表列表**
```
table_name
-------------------
activation_codes
activation_records
admin_users
```

**查询结果 2：管理员账户**
```
id | username    | created_at
---+-------------+-------------------------
1  | LeafStudio  | 2025-11-26 11:49:41.xxx
```

---

## ✅ 成功标志

如果您看到：
- ✅ 3 个表已创建
- ✅ 管理员账户已插入
- ✅ 没有错误提示

**恭喜！数据库初始化成功！**

---

## 🔧 如果遇到错误

### 错误 1: "relation already exists"

**说明**：表已经存在

**解决**：
- 这是正常的，说明表已经创建过了
- 可以继续使用

### 错误 2: "permission denied"

**说明**：权限不足

**解决**：
1. 确保您使用的是数据库所有者账户
2. 检查用户权限

### 错误 3: "syntax error"

**说明**：SQL 语法错误

**解决**：
1. 确保完整复制了所有 SQL
2. 检查是否有多余的字符

---

## 🧪 测试数据库

初始化完成后，可以测试一下：

```sql
-- 测试查询管理员
SELECT * FROM admin_users WHERE username = 'LeafStudio';

-- 应该返回一条记录

-- 测试插入激活码
INSERT INTO activation_codes (code, duration_days, is_used)
VALUES ('TEST-1234-5678', 30, false);

-- 查询激活码
SELECT * FROM activation_codes;
```

---

## 📝 重要信息

### 默认管理员账户

```
用户名: LeafStudio
密码: Test23456
密码哈希: MD5('Test23456')
```

### 表结构说明

**admin_users** - 管理员账户
- `id`: 主键
- `username`: 用户名（唯一）
- `password_hash`: 密码哈希（MD5）
- `created_at`: 创建时间
- `updated_at`: 更新时间
- `last_login`: 最后登录时间

**activation_records** - 激活记录
- `id`: 主键
- `machine_code`: 机器码（唯一）
- `activation_code`: 激活码
- `activation_time`: 激活时间（毫秒时间戳）
- `expiry_time`: 过期时间（毫秒时间戳）
- `device_note`: 设备备注
- `created_at`: 创建时间
- `updated_at`: 更新时间

**activation_codes** - 激活码
- `id`: 主键
- `code`: 激活码（唯一）
- `duration_days`: 有效天数
- `is_used`: 是否已使用
- `used_by_machine`: 使用设备的机器码
- `used_at`: 使用时间（毫秒时间戳）
- `created_at`: 创建时间

---

## 🎯 下一步

数据库初始化完成后：

1. ✅ 上传静态文件到服务器
2. ✅ 访问 https://yezheng.dpdns.org/tv/login.html
3. ✅ 使用默认账户登录
4. ✅ 修改密码
5. ✅ 开始使用！

---

## 💡 快速链接

- **Neon Console**: https://console.neon.tech
- **SQL Editor**: https://console.neon.tech/app/projects/735ebded-3f71-456c-adaf-04564e219725/branches/main/query
- **项目设置**: https://console.neon.tech/app/projects/735ebded-3f71-456c-adaf-04564e219725/settings

---

就是这么简单！复制 SQL，粘贴，运行，完成！🎉
