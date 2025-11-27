#!/bin/bash

# 部署管理后台静态文件到服务器

echo "=== 部署管理后台到 yezheng.dpdns.org/tv/ ==="
echo ""
echo "正在准备上传 web_admin_static/ 目录下的文件..."
echo "- index.html"
echo "- login.html"
echo "- script.js (已修复时间显示问题)"
echo "- style.css"
echo ""

read -p "请输入SSH用户名: " username
read -p "Web根目录路径 (例如 /var/www/html): " webroot

# 确保路径以 / 结尾
[[ "${webroot}" != */ ]] && webroot="${webroot}/"

echo ""
echo "正在上传..."

# 使用 scp 上传所有文件到 /tv/ 目录
scp web_admin_static/* "$username@yezheng.dpdns.org:${webroot}tv/"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ 部署成功！"
    echo ""
    echo "请在浏览器中访问后台，并强制刷新 (Ctrl+F5 或 Shift+F5) 以清除缓存。"
    echo "地址: https://yezheng.dpdns.org/tv/"
else
    echo ""
    echo "❌ 上传失败，请检查SSH连接信息或路径是否正确。"
fi
