# 🚀 快速部署激活API

## 📍 目标位置
- **URL**: `https://yezheng.dpdns.org/tv/api/activation.php`
- **服务器路径**: `/你的网站根目录/tv/api/activation.php`

## 方法1: 使用部署脚本（推荐）⭐

```bash
cd "/Volumes/Ye 1/git_code/LeafStudio"
./deploy_activation.sh
```

按照提示选择部署方式。

---

## 方法2: 手动SCP上传

```bash
# 替换 YOUR_USERNAME 和实际的网站根目录路径
scp "/Volumes/Ye 1/git_code/LeafStudio/api/activation.php" \
    YOUR_USERNAME@yezheng.dpdns.org:/path/to/webroot/tv/api/
```

**常见的网站根目录路径：**
- `/var/www/html`
- `/home/你的用户名/public_html`
- `/usr/share/nginx/html`

---

## 方法3: FTP上传（最简单）

### 使用 FileZilla 或其他FTP客户端：

1. 🔑 连接到 `yezheng.dpdns.org`
2. 📂 导航到网站根目录
3. 📁 进入（或创建）`tv/api/` 文件夹
4. ⬆️ 上传文件：
   - **本地文件**: `/Volumes/Ye 1/git_code/LeafStudio/api/activation.php`
   - **远程位置**: `/tv/api/activation.php`

---

## 方法4: Web控制面板

如果您有 **cPanel** 或 **宝塔面板**：

1. 登录控制面板
2. 打开文件管理器
3. 导航到 `public_html/tv/api/` 或 `html/tv/api/`
4. 上传 `activation.php`
5. 设置文件权限为 `644` 或 `755`

---

## ✅ 部署后验证

运行以下命令测试API：

```bash
curl -X POST https://yezheng.dpdns.org/tv/api/activation.php \
  -H "Content-Type: application/json" \
  -d '{"action":"check","machineCode":"TEST123456789012"}'
```

**成功的响应示例：**
```json
{
  "success": true,
  "isValid": true,
  "remainingSeconds": 259200,
  "expiryTime": 1732863120000,
  "message": "试用期剩余 3 天"
}
```

---

## ⚠️ 注意事项

1. **文件位置必须准确**: `/tv/api/activation.php`
2. **文件权限**: 确保是 `644` 或 `755`
3. **PHP-PDO**: 确保服务器安装了 `pdo_pgsql` 扩展
4. **数据库**: 确保已按 `DATABASE_INIT.md` 初始化数据库

---

## 💡 需要帮助？

如果遇到问题，请提供：
- 您使用的部署方法
- 错误信息（如有）
- 服务器类型（Apache/Nginx/其他）
