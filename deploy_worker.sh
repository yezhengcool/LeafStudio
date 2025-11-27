#!/bin/bash

echo "=== 部署 Cloudflare Worker 激活API ==="
echo ""
echo "正在准备环境..."

cd "$(dirname "$0")/api"

# 检查是否已登录
echo "正在检查 Cloudflare 登录状态..."
if ! npx wrangler whoami &> /dev/null; then
    echo "⚠️  需要登录 Cloudflare"
    echo "👉 浏览器将自动打开，请点击 'Allow' 授权登录..."
    echo ""
    npx wrangler login
fi

echo ""
echo "✅ 已登录 Cloudflare"
echo "正在部署 Worker..."
echo ""

# 部署
npx wrangler deploy worker.js --name leafstudio-activation

if [ $? -eq 0 ]; then
    echo ""
    echo "✅✅✅ 部署成功！"
    echo ""
    echo "⚠️ 关键步骤：请立即配置路由 ⚠️"
    echo ""
    echo "1. 打开: https://dash.cloudflare.com"
    echo "2. 点击域名: yezheng.dpdns.org"
    echo "3. 左侧菜单选择: Workers Routes (Worker 路由)"
    echo "4. 点击 'Add route' (添加路由)"
    echo "5. 填写信息："
    echo "   - Route: yezheng.dpdns.org/api/activation.php"
    echo "   - Worker: leafstudio-activation"
    echo "6. 点击 Save"
    echo ""
    echo "配置完成后，App 即可正常使用！"
else
    echo ""
    echo "❌ 部署失败，请检查错误信息"
fi
