# 激活系统改进方案

## 概述

本次改进将激活码验证方式从**本地算法验证**改为**基于数据库的服务器验证**，实现了更灵活、更安全的激活管理。

---

## 改进内容

### 1. 核心改变

#### 之前的方式（本地验证）
- ✗ 激活码通过算法在本地生成和验证
- ✗ 使用设备本地时间计算剩余时长
- ✗ 容易被破解或篡改本地时间
- ✗ 无法远程管理激活状态

#### 现在的方式（数据库验证）
- ✓ 激活码存储在数据库中
- ✓ 使用服务器网络时间计算剩余时长
- ✓ 防止本地时间篡改
- ✓ 可在服务器端灵活管理激活时间
- ✓ 支持远程查询和更新激活状态

---

## 技术架构

### 客户端（Android App）

**文件**: `app/src/main/java/com/leafstudio/tvplayer/utils/ActivationManager.kt`

**主要功能**:
1. **获取网络时间** - `getNetworkTime()`
   - 从服务器获取准确时间
   - 防止用户篡改本地时间

2. **验证激活码** - `validateActivationCodeFromServer()`
   - 异步调用服务器 API
   - 验证激活码并获取激活信息
   - 自动保存到本地缓存

3. **检查激活状态** - `checkActivationStatus()`
   - 查询设备当前激活状态
   - 获取剩余天数
   - 更新本地缓存

4. **本地缓存** - `isActivated()`, `getRemainingTime()`
   - 快速检查激活状态（离线可用）
   - 定期同步服务器数据

### 服务器端（API）

**需要实现的接口**:

1. **GET /api/time**
   - 返回服务器当前时间戳
   
2. **GET /api/activation/activate**
   - 验证并激活设备
   - 参数: `machine_code`, `activation_code`
   
3. **GET /api/activation/check**
   - 查询设备激活状态
   - 参数: `machine_code`

详细 API 文档: [ACTIVATION_API.md](./ACTIVATION_API.md)

---

## 数据库设计

### 激活记录表 (activation_records)

存储每个设备的激活信息：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| machine_code | VARCHAR(16) | 设备机器码（唯一） |
| activation_code | VARCHAR(50) | 使用的激活码 |
| activation_time | BIGINT | 激活时间戳（毫秒） |
| expiry_time | BIGINT | 过期时间戳（毫秒） |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### 激活码表 (activation_codes)

存储所有可用的激活码：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| code | VARCHAR(50) | 激活码（唯一） |
| duration_days | INT | 有效天数 |
| is_used | BOOLEAN | 是否已使用 |
| used_by_machine | VARCHAR(16) | 使用设备的机器码 |
| used_at | BIGINT | 使用时间戳 |
| created_at | TIMESTAMP | 创建时间 |

---

## 工作流程

### 激活流程

```
用户输入激活码
    ↓
客户端调用 /api/activation/activate
    ↓
服务器验证激活码
    ├─ 检查激活码是否存在
    ├─ 检查激活码是否已被使用
    └─ 计算过期时间 = 当前时间 + 激活天数
    ↓
创建/更新激活记录
    ↓
标记激活码为已使用
    ↓
返回激活信息给客户端
    ↓
客户端保存到本地缓存
    ↓
激活成功！
```

### 状态检查流程

```
应用启动 / 定期检查（每小时）
    ↓
客户端调用 /api/activation/check
    ↓
服务器查询激活记录
    ↓
获取服务器当前时间
    ↓
计算剩余时长 = 过期时间 - 当前时间
    ↓
返回激活状态
    ↓
客户端更新本地缓存
    ↓
显示剩余时间
```

---

## 使用说明

### 1. 服务器端部署

#### 步骤 1: 创建数据库表

```sql
-- 创建激活记录表
CREATE TABLE activation_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    machine_code VARCHAR(16) NOT NULL UNIQUE,
    activation_code VARCHAR(50) NOT NULL,
    activation_time BIGINT NOT NULL,
    expiry_time BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_machine_code (machine_code),
    INDEX idx_expiry_time (expiry_time)
);

-- 创建激活码表
CREATE TABLE activation_codes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL UNIQUE,
    duration_days INT NOT NULL,
    is_used BOOLEAN DEFAULT FALSE,
    used_by_machine VARCHAR(16) DEFAULT NULL,
    used_at BIGINT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_code (code),
    INDEX idx_is_used (is_used)
);
```

#### 步骤 2: 实现 API 接口

参考 [ACTIVATION_API.md](./ACTIVATION_API.md) 中的 PHP 示例代码。

#### 步骤 3: 生成激活码

使用提供的 Python 工具生成激活码：

```bash
cd tools
python3 generate_activation_codes.py
```

按提示输入：
- 生成数量（如：100）
- 有效天数（如：30）

激活码将自动插入数据库并保存到文本文件。

### 2. 客户端使用

客户端代码已经更新，无需额外配置。激活流程：

1. 打开应用
2. 点击菜单 → 激活
3. 复制机器码发送给管理员
4. 管理员提供激活码
5. 输入激活码并点击"激活"
6. 系统自动验证并显示结果

---

## 优势对比

| 特性 | 本地验证 | 数据库验证 |
|------|---------|-----------|
| 安全性 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 防时间篡改 | ❌ | ✅ |
| 远程管理 | ❌ | ✅ |
| 灵活性 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 离线使用 | ✅ | ✅（缓存） |
| 激活码复用 | 可能 | 不可能 |
| 统计分析 | ❌ | ✅ |

---

## 安全特性

1. **防时间篡改**: 使用服务器时间而非本地时间
2. **一码一用**: 每个激活码只能使用一次
3. **设备绑定**: 激活码与设备机器码绑定
4. **HTTPS 加密**: API 通信使用 HTTPS
5. **频率限制**: 可在服务器端限制请求频率
6. **日志审计**: 所有激活记录可追溯

---

## 缓存策略

为了提升性能和支持离线使用，客户端采用智能缓存策略：

1. **首次激活**: 从服务器验证并缓存结果
2. **定期同步**: 每小时检查一次激活状态
3. **离线模式**: 网络不可用时使用缓存数据
4. **时间校准**: 定期获取网络时间校准

---

## 管理功能

服务器端可以实现以下管理功能：

1. **延长激活**: 直接修改数据库中的 `expiry_time`
2. **撤销激活**: 删除激活记录或设置过期时间为过去
3. **查看统计**: 统计激活设备数量、使用情况等
4. **批量管理**: 批量生成、导出激活码
5. **黑名单**: 封禁特定设备的机器码

---

## 示例场景

### 场景 1: 延长用户激活时间

```sql
-- 为特定设备延长30天
UPDATE activation_records 
SET expiry_time = expiry_time + (30 * 24 * 60 * 60 * 1000)
WHERE machine_code = 'A1B2C3D4E5F6G7H8';
```

### 场景 2: 查看即将过期的设备

```sql
-- 查询7天内即将过期的设备
SELECT machine_code, 
       FROM_UNIXTIME(expiry_time/1000) as expiry_date,
       FLOOR((expiry_time - UNIX_TIMESTAMP()*1000) / (24*60*60*1000)) as remaining_days
FROM activation_records
WHERE expiry_time > UNIX_TIMESTAMP()*1000
  AND expiry_time < (UNIX_TIMESTAMP()*1000 + 7*24*60*60*1000)
ORDER BY expiry_time;
```

### 场景 3: 统计激活情况

```sql
-- 统计总激活数、有效激活数、已过期数
SELECT 
    COUNT(*) as total_activations,
    SUM(CASE WHEN expiry_time > UNIX_TIMESTAMP()*1000 THEN 1 ELSE 0 END) as active,
    SUM(CASE WHEN expiry_time <= UNIX_TIMESTAMP()*1000 THEN 1 ELSE 0 END) as expired
FROM activation_records;
```

---

## 常见问题

### Q1: 如果服务器宕机，用户还能使用吗？

A: 可以。客户端会使用本地缓存的激活信息，但会使用网络时间（如果网络时间也获取失败，会降级使用本地时间）。

### Q2: 如何防止用户篡改本地时间？

A: 系统使用网络时间而非本地时间计算剩余时长。即使用户修改本地时间，也不会影响激活状态的判断。

### Q3: 激活码可以重复使用吗？

A: 不可以。每个激活码只能使用一次，使用后会在数据库中标记为已使用。

### Q4: 如何批量生成激活码？

A: 使用提供的 `tools/generate_activation_codes.py` 工具，可以批量生成并自动插入数据库。

### Q5: 可以为不同用户设置不同的有效期吗？

A: 可以。生成激活码时可以指定不同的 `duration_days`，比如 7天、30天、365天等。

---

## 后续优化建议

1. **Web 管理后台**: 开发一个 Web 界面管理激活码和设备
2. **自动续费**: 支持用户自动续费功能
3. **多级激活**: 支持试用版、标准版、专业版等不同级别
4. **设备限制**: 限制单个激活码可激活的设备数量
5. **推送通知**: 激活即将过期时推送提醒

---

## 文件清单

```
LeafStudio/
├── app/src/main/java/com/leafstudio/tvplayer/
│   ├── utils/
│   │   └── ActivationManager.kt          # 激活管理器（已更新）
│   └── PlaybackActivity.kt               # 主活动（已更新）
├── docs/
│   ├── ACTIVATION_API.md                 # API 文档（新增）
│   └── ACTIVATION_UPGRADE.md             # 本文档（新增）
└── tools/
    └── generate_activation_codes.py      # 激活码生成工具（新增）
```

---

## 总结

通过这次改进，激活系统从简单的本地验证升级为基于数据库的服务器验证，大大提升了：

- ✅ **安全性**: 防止破解和时间篡改
- ✅ **灵活性**: 可远程管理激活状态
- ✅ **可控性**: 集中管理所有激活信息
- ✅ **可扩展性**: 易于添加新功能

同时保持了良好的用户体验，支持离线使用和快速响应。
