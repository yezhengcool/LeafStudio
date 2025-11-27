# 部署Cloudflare Worker激活API

## 前提条件
- Cloudflare账号
- `wrangler` CLI工具

## 快速部署步骤

### 1. 安装Wrangler CLI

```bash
npm install -g wrangler
```

### 2. 登录Cloudflare

```bash
wrangler login
```

这会打开浏览器，让您授权访问Cloudflare账号。

### 3. 部署Worker

```bash
cd "/Volumes/Ye 1/git_code/LeafStudio/api"
wrangler deploy worker.js
```

### 4. 配置路由

部署后，您需要在Cloudflare Dashboard中设置路由：

1. 登录 https://dash.cloudflare.com
2. 选择您的域名 `yezheng.dpdns.org`
3. 进入 **Workers Routes**
4. 添加路由：
   - Route: `yezheng.dpdns.org/api/activation.php`
   - Worker: 选择刚部署的worker

### 5. 验证部署

```bash
curl -X POST https://yezheng.dpdns.org/api/activation.php \
  -H "Content-Type: application/json" \
  -d '{"action":"check","machineCode":"TEST123456789012"}'
```

应该返回JSON响应：
```json
{
  "isValid": true,
  "remainingSeconds": 259200,
  "message": "试用期剩余 3 天"
}
```

---

## 🚨 重要说明

Worker.js使用的API路径与PHP版本不同：
- Worker路径：`/api/check` 和 `/api/activate`
- PHP路径：`/api/activation.php?action=check`

我需要修改`worker.js`以适配Android应用的请求格式。

---

## 或者：使用Vercel部署（备选方案）

如果您更熟悉Vercel：

```bash
cd "/Volumes/Ye 1/git_code/LeafStudio"
npm install @neondatabase/serverless
vercel --prod
```

---

让我知道您想用哪个方案，我会帮您调整代码。
