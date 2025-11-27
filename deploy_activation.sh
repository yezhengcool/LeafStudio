#!/bin/bash

# 快速部署 activation.php 到 yezheng.dpdns.org/tv/

echo "=== 部署 activation.php 到服务器 ==="
echo ""
echo "目标: https://yezheng.dpdns.org/tv/api/activation.php"
echo ""
echo "请选择部署方式："
echo "1. 使用 SCP 上传（需要 SSH 访问权限）"
echo "2. 显示手动部署说明"
echo ""

read -p "请选择 (1 或 2): " choice

if [ "$choice" = "1" ]; then
    echo ""
    read -p "请输入SSH用户名: " username
    read -p "Web根目录路径 (例如: /var/www/html 或 /home/你的用户名/public_html): " webroot
    
    echo ""
    echo "正在上传 activation.php 到服务器..."
    
    # 创建 tv/api 目录（如果不存在）
    ssh "$username@yezheng.dpdns.org" "mkdir -p $webroot/tv/api"
    
    # 上传文件
    scp "api/activation.php" "$username@yezheng.dpdns.org:$webroot/tv/api/"
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ 上传成功！"
        echo ""
        echo "API地址: https://yezheng.dpdns.org/tv/api/activation.php"
        echo ""
        echo "正在测试API..."
        sleep 2
        curl -X POST https://yezheng.dpdns.org/tv/api/activation.php \
          -H "Content-Type: application/json" \
          -d '{"action":"check","machineCode":"TEST123456789012"}'
        echo ""
        echo ""
        echo "如果看到JSON响应，说明部署成功！"
    else
        echo "❌ 上传失败，请检查SSH连接信息"
    fi
else
    echo ""
    echo "=== 手动部署说明 ==="
    echo ""
    echo "📋 步骤："
    echo ""
    echo "1️⃣  使用FTP客户端（如 FileZilla）连接到 yezheng.dpdns.org"
    echo "    或者登录您的服务器控制面板（cPanel、宝塔等）"
    echo ""
    echo "2️⃣  找到网站根目录（通常是以下之一）："
    echo "    - /var/www/html"
    echo "    - /home/你的用户名/public_html"
    echo "    - /usr/share/nginx/html"
    echo ""
    echo "3️⃣  进入 tv/api 文件夹（如果不存在则创建）"
    echo ""
    echo "4️⃣  上传文件："
    echo "    源文件: $(pwd)/api/activation.php"
    echo "    目标位置: /tv/api/activation.php"
    echo ""
    echo "5️⃣  确保文件权限正确："
    echo "    - 文件权限应该是 644 或 755"
    echo ""
    echo "6️⃣  测试API（上传后执行）："
    echo ""
    echo "    curl -X POST https://yezheng.dpdns.org/tv/api/activation.php \\"
    echo "      -H 'Content-Type: application/json' \\"
    echo "      -d '{\"action\":\"check\",\"machineCode\":\"TEST123456789012\"}'"
    echo ""
    echo "7️⃣  成功的响应应该类似："
    echo "    {\"success\":true,\"isValid\":true,\"message\":\"试用期剩余 3 天\"}"
    echo ""
fi

echo ""
echo "完成！"
