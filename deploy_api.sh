#!/bin/bash

# 部署激活API到服务器
# 使用SCP或FTP上传activation.php到web服务器

echo "=== LeafStudio 激活API部署脚本 ==="
echo ""
echo "请选择部署方式："
echo "1. 使用SCP上传（需要SSH访问）"
echo "2. 手动部署说明"
echo "3. 使用FTP上传"
echo ""

read -p "请输入选项 (1-3): " choice

case $choice in
  1)
    echo ""
    echo "请提供SSH连接信息："
    read -p "服务器地址 (例如: yezheng.dpdns.org): " server
    read -p "SSH用户名: " username
    read -p "Web根目录路径 (例如: /var/www/html): " webroot
    
    echo ""
    echo "正在上传 activation.php 到 $server:$webroot/api/ ..."
    
    # 创建api目录
    ssh "$username@$server" "mkdir -p $webroot/api"
    
    # 上传文件
    scp api/activation.php "$username@$server:$webroot/api/"
    
    if [ $? -eq 0 ]; then
      echo "✅ 上传成功！"
      echo ""
      echo "API地址: https://$server/api/activation.php"
      echo ""
      echo "请测试API:"
      echo "curl -X POST https://$server/api/activation.php \\"
      echo "  -H 'Content-Type: application/json' \\"
      echo "  -d '{\"action\":\"check\",\"machineCode\":\"TEST1234567890AB\"}'"
    else
      echo "❌ 上传失败，请检查连接信息"
    fi
    ;;
    
  2)
    echo ""
    echo "=== 手动部署步骤 ==="
    echo ""
    echo "1. 使用FTP客户端或服务器管理面板"
    echo "2. 在web根目录创建 'api' 文件夹"
    echo "3. 上传文件: api/activation.php"
    echo "4. 确保PHP有权限连接到PostgreSQL（可能需要安装pdo_pgsql扩展）"
    echo ""
    echo "部署后，测试API:"
    echo "curl -X POST https://your-domain.com/api/activation.php \\"
    echo "  -H 'Content-Type: application/json' \\"
    echo "  -d '{\"action\":\"check\",\"machineCode\":\"TEST1234567890AB\"}'"
    ;;
    
  3)
    echo ""
    echo "FTP部署需要FTP客户端"
    echo "推荐使用: FileZilla, Cyberduck"
    echo ""
    echo "FTP配置:"
    read -p "FTP服务器: " ftp_server
    read -p "FTP用户名: " ftp_user
    echo ""
    echo "请使用FTP客户端:"
    echo "1. 连接到 $ftp_server"
    echo "2. 进入网站根目录"
    echo "3. 创建 'api' 文件夹"
    echo "4. 上传 api/activation.php"
    ;;
    
  *)
    echo "无效选项"
    ;;
esac

echo ""
echo "完成！"
