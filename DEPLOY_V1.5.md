# ✅ LeafStudio v1.5 激活版 - 部署说明

**生成时间**: 2025-11-27 09:18
**APK位置**: `~/Desktop/LeafStudio-v1.5-激活版.apk` (15MB)

---

## ⚠️ 重要：必须先部署API

**在安装APK之前，您必须先部署激活API，否则应用无法打开！**

---

## 📋 部署步骤

### 第1步：部署激活API ⭐⭐⭐

激活API必须部署到服务器，否则应用会提示"无法连接服务器"并退出。

#### 方法1: 使用自动化脚本（推荐）

```bash
cd "/Volumes/Ye 1/git_code/LeafStudio"
./deploy_activation.sh
```

按提示选择部署方式。

#### 方法2: 手动FTP上传（最简单）

1. 使用FTP客户端（FileZilla等）连接到 `yezheng.dpdns.org`
2. 导航到网站根目录的 `tv/api/` 文件夹
3. 上传文件：
   - **源文件**: `/Volumes/Ye 1/git_code/LeafStudio/api/activation.php`
   - **目标位置**: `/tv/api/activation.php`
4. 设置文件权限为 `644` 或 `755`

#### 方法3: SCP命令上传

```bash
# 替换 YOUR_USERNAME 和实际路径
scp "/Volumes/Ye 1/git_code/LeafStudio/api/activation.php" \
    YOUR_USERNAME@yezheng.dpdns.org:/path/to/webroot/tv/api/
```

### 第2步：验证API部署成功

运行以下命令测试：

```bash
curl -X POST https://yezheng.dpdns.org/tv/api/activation.php \
  -H "Content-Type: application/json" \
  -d '{"action":"check","machineCode":"TEST123456789012"}'
```

**成功的响应示例**：
```json
{
  "success": true,
  "isValid": true,
  "remainingSeconds": 259200,
  "expiryTime": 1732863120000,
  "message": "试用期剩余 3 天"
}
```

**如果看到405或404错误**，说明API还没有部署成功，请检查文件路径。

---

### 第3步：确保数据库已初始化

如果还没有初始化数据库，请按照 `DATABASE_INIT.md` 完成初始化。

---

### 第4步：安装APK到设备

1. 将 `LeafStudio-v1.5-激活版.apk` 传输到Android设备
2. 安装应用
3. 首次打开时：
   - ✅ 应用会自动连接API
   - ✅ API会自动注册设备到数据库（3天试用期）
   - ✅ 管理后台会显示设备信息

---

## 🎯 激活系统工作流程

### 用户首次打开App时：

1. **App调用API检查激活状态**
   ```
   POST https://yezheng.dpdns.org/tv/api/activation.php
   { "action": "check", "machineCode": "设备机器码" }
   ```

2. **API自动注册设备**
   - 将机器码、注册时间写入数据库
   - 自动给予3天试用期
   - 返回激活状态给App

3. **管理后台显示设备**
   - 在 `https://yezheng.dpdns.org/tv/` 登录管理后台
   - 可以看到所有注册的设备
   - 可以生成激活码并管理设备

### 试用期结束后：

1. App会提示"激活已过期"
2. 用户需要输入激活码
3. 管理员在后台生成激活码
4. 用户输入激活码后，设备被正式激活

---

## 🔧 故障排除

### 问题1: 打开App提示"无法连接服务器"

**原因**: API还没有部署
**解决**: 完成第1步，部署activation.php到服务器

### 问题2: API返回405错误

**原因**: 文件路径不正确
**解决**: 确保文件在 `/tv/api/activation.php`，不是 `/tv/activation.php`

### 问题3: API返回"Database error"

**原因**: 数据库表还没有创建
**解决**: 按照 `DATABASE_INIT.md` 初始化数据库

---

## 📁 相关文件

- **APK**: `~/Desktop/LeafStudio-v1.5-激活版.apk`
- **API文件**: `/Volumes/Ye 1/git_code/LeafStudio/api/activation.php`
- **部署脚本**: `/Volumes/Ye 1/git_code/LeafStudio/deploy_activation.sh`
- **数据库初始化**: `/Volumes/Ye 1/git_code/LeafStudio/DATABASE_INIT.md`
- **快速部署指南**: `/Volumes/Ye 1/git_code/LeafStudio/QUICK_DEPLOY_API.md`

---

## ✨ 更新内容（v1.5）

1. ✅ 激活系统与管理后台完全同步
2. ✅ 首次打开自动注册设备到数据库
3. ✅ 管理员可以在后台查看所有设备
4. ✅ 支持激活码激活和续期
5. ✅ API必须可用，确保数据一致性

---

## 📞 下一步

1. **立即部署API** → 运行 `./deploy_activation.sh` 或手动上传
2. **验证API** → 测试API是否正常响应
3. **安装APK** → 安装到设备并测试
4. **登录管理后台** → 查看设备注册信息

---

**重要提醒**: 没有部署API，应用无法打开！请务必先完成第1步。
