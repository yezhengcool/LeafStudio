# 部署激活 API 到 Vercel

## 步骤 1: 安装 Vercel CLI

```bash
npm install -g vercel
```

## 步骤 2: 登录 Vercel

```bash
vercel login
```

## 步骤 3: 部署项目

在项目根目录执行：

```bash
cd /Volumes/Ye\ 1/git_code/LeafStudio
vercel
```

按照提示进行配置：
- Set up and deploy? **Y**
- Which scope? 选择你的账号
- Link to existing project? **N**
- Project name? **leafstudio-api** (或其他名称)
- In which directory is your code located? **./**

## 步骤 4: 配置环境变量

部署后，在 Vercel Dashboard 中设置环境变量：

1. 打开 https://vercel.com/dashboard
2. 选择 `leafstudio-api` 项目
3. 进入 Settings → Environment Variables
4. 添加环境变量：
   - Name: `DATABASE_URL`
   - Value: `postgresql://neondb_owner:npg_kf5BO3mHDoTZ@ep-sparkling-river-ah52my74-pooler.c-3.us-east-1.aws.neon.tech/neondb?sslmode=require`
   - Environment: Production, Preview, Development (全选)

## 步骤 5: 重新部署

添加环境变量后，重新部署：

```bash
vercel --prod
```

## 步骤 6: 获取 API URL

部署成功后，Vercel会显示你的 API 地址，类似：
```
https://your-project-name.vercel.app
```

## 步骤 7: 更新 Android 代码

将 `ActivationManager.kt` 中的 `API_URL` 更新为你的实际 API 地址：

```kotlin
private const val API_URL = "https://your-project-name.vercel.app/api/activation"
```

## 测试 API

部署后，可以使用 curl 测试：

```bash
curl -X POST https://your-project-name.vercel.app/api/activation \
  -H "Content-Type: application/json" \
  -d '{"action":"check","machineCode":"TEST1234567890AB"}'
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
