# 数据库连接方式变更说明

## 🔄 重要变更

由于 Neon REST API 需要 JWT token，而控制台没有生成 API Key 的选项，我们已经将连接方式改为：

**使用 PDO 直接连接 PostgreSQL 数据库**

---

## ✅ 优点

1. **无需 API Key** - 直接使用数据库用户名和密码
2. **更简单** - 标准的 PDO 连接方式
3. **更快** - 直接连接，无需 HTTP 请求
4. **更稳定** - 使用成熟的 PDO 驱动

---

## 📝 配置信息

`server/config.php` 已更新为：

```php
// 数据库连接配置
define('DB_HOST', 'ep-sparkling-river-ah52my74-pooler.c-3.us-east-1.aws.neon.tech');
define('DB_NAME', 'neondb');
define('DB_USER', 'neondb_owner');
define('DB_PASS', 'npg_kf5BO3mHDoTZ');
define('DB_PORT', '5432');
```

---

## 🚀 部署步骤

### 1. 确保服务器支持 PDO PostgreSQL

检查服务器是否安装了 PDO PostgreSQL 扩展：

```bash
php -m | grep pdo_pgsql
```

如果没有，需要安装：

```bash
# Ubuntu/Debian
sudo apt-get install php-pgsql

# CentOS/RHEL
sudo yum install php-pgsql

# 重启 PHP-FPM
sudo systemctl restart php-fpm
```

### 2. 上传文件

```bash
# 上传整个 server 文件夹
scp -r server/* user@server:/var/www/html/tv/api/

# 上传静态网站
scp web_admin/*.html web_admin/*.css web_admin/*.js user@server:/var/www/html/tv/
```

### 3. 初始化数据库

在 Neon Console 的 SQL Editor 中执行初始化脚本（见下方）

### 4. 测试连接

创建测试文件 `test_db.php`：

```php
<?php
require_once 'config.php';

$pdo = getDBConnection();
if ($pdo) {
    echo "✅ 数据库连接成功！\n";
    
    // 测试查询
    $stmt = executeQuery("SELECT version()");
    if ($stmt) {
        $result = $stmt->fetch();
        echo "PostgreSQL 版本: " . $result['version'] . "\n";
    }
} else {
    echo "❌ 数据库连接失败！\n";
}
```

访问：`https://yezheng.dpdns.org/tv/api/test_db.php`

---

## 🗄️ 数据库初始化 SQL

在 Neon Console 执行：

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
CREATE INDEX IF NOT EXISTS idx_expiry_time ON activation_records(expiry_time);

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
CREATE INDEX IF NOT EXISTS idx_is_used ON activation_codes(is_used);

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

-- 验证表已创建
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;
```

---

## ⚠️ 注意事项

### 1. PDO 扩展

确保服务器安装了 `pdo_pgsql` 扩展。

### 2. SSL 连接

Neon 要求 SSL 连接，配置中已包含 `sslmode=require`。

### 3. 连接池

使用了 Neon 的 pooler 地址（`-pooler`），性能更好。

### 4. 密码安全

- 不要将 `config.php` 提交到 Git
- 或者使用环境变量存储密码

---

## 🧪 测试清单

- [ ] 服务器已安装 `pdo_pgsql` 扩展
- [ ] 数据库表已创建
- [ ] 默认管理员账户已创建
- [ ] 测试数据库连接成功
- [ ] 测试登录 API
- [ ] 测试设备管理功能

---

## 📞 故障排查

### 问题 1: "could not find driver"

**原因**: 缺少 PDO PostgreSQL 扩展

**解决**:
```bash
sudo apt-get install php-pgsql
sudo systemctl restart php-fpm
```

### 问题 2: "Connection refused"

**原因**: 防火墙或网络问题

**解决**:
1. 检查服务器是否能访问 Neon
2. 测试连接：`telnet ep-sparkling-river-ah52my74-pooler.c-3.us-east-1.aws.neon.tech 5432`

### 问题 3: "Access denied"

**原因**: 用户名或密码错误

**解决**:
检查 `config.php` 中的 `DB_USER` 和 `DB_PASS` 是否正确。

---

现在配置更简单了，无需 API Key！🎉
