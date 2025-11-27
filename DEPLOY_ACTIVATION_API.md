# 部署激活API到服务器

## 方法1：使用FTP/SFTP客户端（推荐）

### 步骤：
1. 使用FTP客户端（如FileZilla）连接到服务器 `yezheng.dpdns.org`
2. 导航到 `/tv/` 目录
3. 上传 `api/activation.php` 文件到 `/tv/activation.php`
4. 确保文件权限设置为 644 或 755

## 方法2：使用命令行SCP

如果您有SSH访问权限，可以使用以下命令：

```bash
scp /Volumes/Ye\ 1/git_code/LeafStudio/api/activation.php your_username@yezheng.dpdns.org:/path/to/web/root/tv/
```

## 方法3：使用cPanel或Web控制面板

1. 登录您的主机控制面板（cPanel、宝塔等）
2. 进入文件管理器
3. 导航到 `/tv/` 目录
4. 上传 `activation.php` 文件

## 验证部署

部署完成后，使用以下命令验证：

```bash
curl -X POST https://yezheng.dpdns.org/tv/activation.php \
  -H "Content-Type: application/json" \
  -d '{"action":"check","machineCode":"TEST123456789012"}'
```

应该返回类似：
```json
{
  "success": true,
  "isValid": true,
  "remainingSeconds": 259200,
  "expiryTime": 1732863120000,
  "message": "试用期剩余 3 天"
}
```

## 重要提示

1. **数据库配置**：确保 `activation.php` 中的数据库连接信息正确
2. **SSL证书**：建议使用HTTPS（已自动重定向）
3. **文件权限**：确保PHP文件可执行（644或755）
4. **数据库初始化**：确保已按照 `DATABASE_INIT.md` 完成数据库初始化

## 当前配置

- **API端点**: `https://yezheng.dpdns.org/tv/activation.php`
- **数据库**: Neon PostgreSQL (已配置在activation.php中)
- **Android应用API URL**: 已更新到 `ActivationManager.kt`
