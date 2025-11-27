# API 部署指南

## 方案一：Cloudflare Workers（推荐）

### 1. 注册 Cloudflare 账号
访问 https://dash.cloudflare.com/sign-up

### 2. 创建 Worker
1. 进入 Workers & Pages
2. 点击 "Create application"
3. 选择 "Create Worker"
4. 命名为 `leafstudio-activation`

### 3. 部署代码
1. 将 `api/worker.js` 的内容复制到 Worker 编辑器
2. 点击 "Save and Deploy"
3. 记录下 Worker URL，例如：`https://leafstudio-activation.your-username.workers.dev`

### 4. 更新 App 配置
修改 `app/src/main/java/com/leafstudio/tvplayer/utils/ActivationManager.kt`：
```kotlin
private const val API_BASE_URL = "https://leafstudio-activation.your-username.workers.dev"
```

### 5. 重新构建 APK
```bash
cd /Volumes/Ye\ 1/git_code/LeafStudio
./gradlew clean assembleRelease
```

---

## 方案二：Vercel Edge Functions

### 1. 安装 Vercel CLI
```bash
npm install -g vercel
```

### 2. 创建项目
```bash
cd api
vercel login
vercel
```

### 3. 部署
按照提示完成部署，Vercel 会给你一个 URL。

### 4. 更新 App 配置
同上，修改 `ActivationManager.kt` 中的 `API_BASE_URL`。

---

## 测试 API

### 检查激活状态
```bash
curl -X POST https://your-worker.workers.dev/api/check \
  -H "Content-Type: application/json" \
  -d '{"machineCode":"TEST1234567890AB"}'
```

### 激活设备
```bash
curl -X POST https://your-worker.workers.dev/api/activate \
  -H "Content-Type: application/json" \
  -d '{"machineCode":"TEST1234567890AB","activationCode":"YOUR-CODE-HERE"}'
```

---

## 注意事项

1. **免费额度**：Cloudflare Workers 每天有 100,000 次免费请求。
2. **安全性**：数据库凭据硬编码在 Worker 中，但 Worker 代码不会暴露给客户端。
3. **性能**：Worker 部署在全球边缘节点，响应速度极快。
