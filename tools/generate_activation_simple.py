#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LeafStudio 激活码生成器 (简化版)

使用方法:
python3 generate_activation_simple.py <机器码> [天数]

示例:
python3 generate_activation_simple.py A1B2C3D4E5F6G7H8 30
python3 generate_activation_simple.py A1B2C3D4E5F6G7H8 365
"""

import sys
import base64
from datetime import datetime, timedelta
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad

# 密钥 - 必须与 App 中的密钥一致
SECRET_KEY = "LeafStudio2024!@"

def generate_activation_code(machine_code, expiry_date):
    """生成激活码"""
    try:
        data = f"{machine_code}|{expiry_date}"
        key = SECRET_KEY.ljust(16, '0')[:16].encode('utf-8')
        cipher = AES.new(key, AES.MODE_ECB)
        encrypted = cipher.encrypt(pad(data.encode('utf-8'), AES.block_size))
        base64_str = base64.b64encode(encrypted).decode('utf-8')
        return format_activation_code(base64_str)
    except Exception as e:
        print(f"错误: {e}")
        return ""

def format_activation_code(code):
    """格式化激活码"""
    cleaned = ''.join(c for c in code if c.isalnum())
    formatted = '-'.join([cleaned[i:i+4] for i in range(0, len(cleaned), 4)])
    return formatted.upper()

def main():
    if len(sys.argv) < 2:
        print("使用方法: python3 generate_activation_simple.py <机器码> [天数]")
        print("示例: python3 generate_activation_simple.py A1B2C3D4E5F6G7H8 30")
        sys.exit(1)
    
    machine_code = sys.argv[1]
    days = int(sys.argv[2]) if len(sys.argv) > 2 else 30
    
    expiry_date = datetime.now() + timedelta(days=days)
    expiry_str = expiry_date.strftime("%Y-%m-%d %H:%M:%S")
    
    activation_code = generate_activation_code(machine_code, expiry_str)
    
    print("=" * 60)
    print("LeafStudio 激活码生成器".center(60))
    print("=" * 60)
    print(f"\n机器码:     {machine_code}")
    print(f"有效期:     {days} 天")
    print(f"过期时间:   {expiry_str}")
    print(f"\n激活码:     {activation_code}\n")
    print("=" * 60)

if __name__ == "__main__":
    main()
