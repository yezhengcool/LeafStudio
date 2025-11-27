# 激活系统修复指南

## 🚀 问题已修复

原因：Neon数据库不支持直接的HTTP SQL API，需要通过中间层访问。

## 📋 解决方案

已创建PHP API作为中间层，需要部署到您的web服务器。

## 🔧 部署步骤（简单3步）

### 步骤 1: 上传API文件

将 `api/activation.php` 文件上传到您的web服务器：

```
服务器: yezheng.dpdns.org
目标路径: /var/www/html/api/activation.php  (或您的web根目录/api/)
```

### 步骤 2: 确保PHP扩展

确保服务器安装了PostgreSQL扩展：

```bash
# 检查是否已安装
php -m |  grep pdo_pgsql

# 如果未安装，根据系统安装:
# Ubuntu/Debian:
sudo apt-get install php-pgsql

# CentOS/RHEL:
sudo yum install php-pgsql

# 重启PHP服务
sudo systemctl restart php-fpm  # 或 apache2/nginx
```

### 步骤 3: 测试API

```bash
curl -X POST https://yezheng.dpdns.org/api/activation.php \
  -H "Content-Type: application/json" \
  -d '{"action":"check","machineCode":"TEST1234567890AB"}'
```

成功响应示例：
```json
{
  "success": true,
  "isValid": true,
  "remainingSeconds": 259200,
  "expiryTime": 1733035200000,
  "message": "试用期剩余 3 天"
}
```

## ✅ 完成后

1. 编译新APK：
```bash
cd /Volumes/Ye\ 1/git_code/LeafStudio
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk ~/Desktop/LeafStudio_v1.6_api_fixed.apk
```

2. 安装并测试应用的激活功能

## 🔒 安全建议（可选）

如果需要更高安全性，可以：
1. 修改 `activation.php` 中的数据库凭证为环境变量
2. 添加请求频率限制
3. 添加IP白名单

## 📁 文件说明

- `/api/activation.php` - PHP API（需上传到服务器）
- `/api/activation.js` - Vercel版本（备用方案）
- `/deploy_api.sh` - 自动部署脚本（可选使用）

## ❓ 如果没有SSH访问权限

使用FTP客户端（如FileZilla）：
1. 连接到 yezheng.dpdns.org
2. 进入网站根目录
3. 创建 `api` 文件夹
4. 上传activation.php
