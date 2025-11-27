# 激活系统 API 文档

## 概述
本文档描述了基于数据库的激活系统所需的服务器端 API 接口。

## 基础信息
- **Base URL**: `https://yezheng.dpdns.org/tv/api`
- **响应格式**: JSON
- **字符编码**: UTF-8

---

## 1. 获取服务器时间

### 接口地址
```
GET /time
```

### 功能说明
返回服务器当前时间戳，用于防止客户端时间篡改。

### 请求参数
无

### 响应示例
```json
{
  "timestamp": 1732588479000,
  "datetime": "2025-11-26 09:14:39"
}
```

### 字段说明
| 字段 | 类型 | 说明 |
|------|------|------|
| timestamp | Long | 服务器时间戳（毫秒） |
| datetime | String | 格式化的时间字符串 |

---

## 2. 验证并激活

### 接口地址
```
GET /activation/activate
```

### 功能说明
验证激活码并激活设备，如果激活码有效则在数据库中创建或更新激活记录。

### 请求参数
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| machine_code | String | 是 | 设备机器码（16位） |
| activation_code | String | 是 | 激活码 |

### 请求示例
```
GET /activation/activate?machine_code=A1B2C3D4E5F6G7H8&activation_code=ABC123XYZ
```

### 响应示例（成功）
```json
{
  "status": "success",
  "message": "激活成功",
  "data": {
    "activation_time": 1732588479000,
    "expiry_time": 1735180479000,
    "remaining_days": 30
  }
}
```

### 响应示例（失败）
```json
{
  "status": "error",
  "message": "激活码无效或已被使用",
  "data": null
}
```

### 字段说明
| 字段 | 类型 | 说明 |
|------|------|------|
| status | String | 状态：success/error |
| message | String | 提示信息 |
| data.activation_time | Long | 激活时间戳（毫秒） |
| data.expiry_time | Long | 过期时间戳（毫秒） |
| data.remaining_days | Int | 剩余天数 |

---

## 3. 检查激活状态

### 接口地址
```
GET /activation/check
```

### 功能说明
查询设备的激活状态，返回激活信息和剩余时间。

### 请求参数
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| machine_code | String | 是 | 设备机器码（16位） |

### 请求示例
```
GET /activation/check?machine_code=A1B2C3D4E5F6G7H8
```

### 响应示例（已激活）
```json
{
  "status": "success",
  "message": "设备已激活",
  "data": {
    "is_activated": true,
    "activation_time": 1732588479000,
    "expiry_time": 1735180479000,
    "remaining_days": 30,
    "message": "激活有效"
  }
}
```

### 响应示例（未激活）
```json
{
  "status": "success",
  "message": "设备未激活",
  "data": {
    "is_activated": false,
    "activation_time": 0,
    "expiry_time": 0,
    "remaining_days": 0,
    "message": "未找到激活记录"
  }
}
```

### 响应示例（已过期）
```json
{
  "status": "success",
  "message": "激活已过期",
  "data": {
    "is_activated": false,
    "activation_time": 1732588479000,
    "expiry_time": 1733000000000,
    "remaining_days": 0,
    "message": "激活已于 2025-11-20 过期"
  }
}
```

### 字段说明
| 字段 | 类型 | 说明 |
|------|------|------|
| status | String | 状态：success/error |
| message | String | 提示信息 |
| data.is_activated | Boolean | 是否已激活且有效 |
| data.activation_time | Long | 激活时间戳（毫秒） |
| data.expiry_time | Long | 过期时间戳（毫秒） |
| data.remaining_days | Int | 剩余天数 |
| data.message | String | 详细消息 |

---

## 数据库设计建议

### 激活记录表 (activation_records)

```sql
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
```

### 激活码表 (activation_codes)

```sql
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

---

## 业务逻辑说明

### 激活流程
1. 客户端输入激活码
2. 调用 `/activation/activate` 接口
3. 服务器验证激活码：
   - 检查激活码是否存在
   - 检查激活码是否已被使用
   - 如果有效，计算过期时间 = 当前时间 + 激活天数
4. 在数据库中创建或更新激活记录
5. 标记激活码为已使用
6. 返回激活信息

### 状态检查流程
1. 客户端启动时或定期调用 `/activation/check` 接口
2. 服务器查询数据库中的激活记录
3. 获取服务器当前时间
4. 比较当前时间与过期时间
5. 计算剩余天数 = (过期时间 - 当前时间) / (24 * 60 * 60 * 1000)
6. 返回激活状态

### 时间计算
- 所有时间使用服务器时间，防止客户端时间篡改
- 时间戳使用毫秒级精度
- 剩余天数向下取整

---

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 1001 | 参数缺失 |
| 1002 | 激活码格式错误 |
| 1003 | 激活码不存在 |
| 1004 | 激活码已被使用 |
| 1005 | 机器码格式错误 |
| 2001 | 数据库错误 |
| 2002 | 服务器内部错误 |

---

## 安全建议

1. **HTTPS**: 所有 API 必须使用 HTTPS 加密传输
2. **频率限制**: 对激活接口进行频率限制，防止暴力破解
3. **日志记录**: 记录所有激活尝试，便于审计
4. **激活码加密**: 激活码在数据库中应加密存储
5. **IP 限制**: 可选择性地限制单个 IP 的请求频率

---

## 客户端缓存策略

1. **本地缓存**: 客户端保存激活信息到 SharedPreferences
2. **定期检查**: 每小时检查一次激活状态（通过 `shouldRecheckActivation()`）
3. **离线模式**: 如果网络不可用，使用本地缓存判断（但使用网络时间）
4. **缓存更新**: 每次成功查询后更新本地缓存

---

## 示例 PHP 实现（参考）

```php
<?php
// /activation/activate
header('Content-Type: application/json');

$machine_code = $_GET['machine_code'] ?? '';
$activation_code = $_GET['activation_code'] ?? '';

// 验证参数
if (empty($machine_code) || empty($activation_code)) {
    echo json_encode([
        'status' => 'error',
        'message' => '参数缺失',
        'data' => null
    ]);
    exit;
}

// 查询激活码
$stmt = $pdo->prepare("SELECT * FROM activation_codes WHERE code = ? AND is_used = 0");
$stmt->execute([$activation_code]);
$code_info = $stmt->fetch();

if (!$code_info) {
    echo json_encode([
        'status' => 'error',
        'message' => '激活码无效或已被使用',
        'data' => null
    ]);
    exit;
}

// 计算过期时间
$activation_time = time() * 1000; // 毫秒
$expiry_time = $activation_time + ($code_info['duration_days'] * 24 * 60 * 60 * 1000);

// 创建或更新激活记录
$stmt = $pdo->prepare("
    INSERT INTO activation_records (machine_code, activation_code, activation_time, expiry_time)
    VALUES (?, ?, ?, ?)
    ON DUPLICATE KEY UPDATE 
        activation_code = VALUES(activation_code),
        activation_time = VALUES(activation_time),
        expiry_time = VALUES(expiry_time)
");
$stmt->execute([$machine_code, $activation_code, $activation_time, $expiry_time]);

// 标记激活码为已使用
$stmt = $pdo->prepare("UPDATE activation_codes SET is_used = 1, used_by_machine = ?, used_at = ? WHERE code = ?");
$stmt->execute([$machine_code, $activation_time, $activation_code]);

echo json_encode([
    'status' => 'success',
    'message' => '激活成功',
    'data' => [
        'activation_time' => $activation_time,
        'expiry_time' => $expiry_time,
        'remaining_days' => $code_info['duration_days']
    ]
]);
?>
```
