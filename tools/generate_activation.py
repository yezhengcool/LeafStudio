#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LeafStudio 激活码生成器 (Python版本)

使用方法:
python3 generate_activation.py

或直接运行:
./generate_activation.py
"""

import base64
import hashlib
from datetime import datetime, timedelta
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad

# 密钥 - 必须与 App 中的密钥一致
SECRET_KEY = "LeafStudio2024!@"

def generate_activation_code(machine_code, expiry_date):
    """
    生成激活码
    
    Args:
        machine_code: 机器码
        expiry_date: 过期时间字符串 (格式: yyyy-MM-dd HH:mm:ss)
    
    Returns:
        格式化的激活码
    """
    try:
        # 组合数据
        data = f"{machine_code}|{expiry_date}"
        
        # 准备密钥 (16字节)
        key = SECRET_KEY.ljust(16, '0')[:16].encode('utf-8')
        
        # AES 加密
        cipher = AES.new(key, AES.MODE_ECB)
        encrypted = cipher.encrypt(pad(data.encode('utf-8'), AES.block_size))
        
        # Base64 编码
        base64_str = base64.b64encode(encrypted).decode('utf-8')
        
        # 格式化
        return format_activation_code(base64_str)
    except Exception as e:
        print(f"生成激活码失败: {e}")
        return ""

def format_activation_code(code):
    """格式化激活码为 XXXX-XXXX-XXXX-XXXX 格式"""
    # 只保留字母和数字
    cleaned = ''.join(c for c in code if c.isalnum())
    
    # 每4个字符添加一个连字符
    formatted = '-'.join([cleaned[i:i+4] for i in range(0, len(cleaned), 4)])
    
    return formatted.upper()

def generate_activation_code_by_days(machine_code, days):
    """
    生成指定天数后过期的激活码
    
    Args:
        machine_code: 机器码
        days: 有效天数
    
    Returns:
        激活码
    """
    expiry_date = datetime.now() + timedelta(days=days)
    expiry_str = expiry_date.strftime("%Y-%m-%d %H:%M:%S")
    
    return generate_activation_code(machine_code, expiry_str), expiry_str

def main():
    """主程序"""
    print("=" * 60)
    print("LeafStudio 激活码生成器".center(60))
    print("=" * 60)
    print()
    
    # 输入机器码
    machine_code = input("请输入机器码: ").strip()
    if not machine_code:
        print("错误: 机器码不能为空")
        return
    
    # 输入有效天数
    try:
        days = int(input("请输入有效天数 (默认30天): ").strip() or "30")
    except ValueError:
        print("错误: 请输入有效的数字")
        return
    
    print()
    print("-" * 60)
    print("正在生成激活码...")
    print("-" * 60)
    
    # 生成激活码
    activation_code, expiry_date = generate_activation_code_by_days(machine_code, days)
    
    if activation_code:
        print()
        print("✓ 生成成功!")
        print()
        print(f"机器码:     {machine_code}")
        print(f"有效期:     {days} 天")
        print(f"过期时间:   {expiry_date}")
        print()
        print(f"激活码:     {activation_code}")
        print()
        print("=" * 60)
        print("请将激活码提供给用户".center(60))
        print("=" * 60)
    else:
        print("生成失败!")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n已取消")
    except Exception as e:
        print(f"\n错误: {e}")
