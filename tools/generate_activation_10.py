#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LeafStudio 激活码生成器 (10位版本)

使用方法:
python3 generate_activation_10.py <机器码> [天数]

示例:
python3 generate_activation_10.py A1B2C3D4E5F6G7H8 30
python3 generate_activation_10.py A1B2C3D4E5F6G7H8 365
"""

import sys
import hashlib
from datetime import datetime, timedelta
from zlib import crc32

def calculate_checksum(code):
    """计算校验码 (Luhn算法)"""
    total = 0
    alternate = False
    
    for i in range(len(code) - 1, -1, -1):
        n = int(code[i])
        if alternate:
            n *= 2
            if n > 9:
                n -= 9
        total += n
        alternate = not alternate
    
    return str((10 - (total % 10)) % 10)

def generate_activation_code(machine_code, expiry_date_str):
    """
    生成10位激活码
    算法: CRC32(机器码,4位) + 过期天数(5位) + 校验码(1位)
    """
    try:
        # 解析过期时间,转换为天数
        expiry_date = datetime.strptime(expiry_date_str, "%Y-%m-%d %H:%M:%S")
        expiry_days = int(expiry_date.timestamp() / (24 * 60 * 60))
        
        # 计算机器码的CRC32值(取后4位)
        machine_hash = crc32(machine_code.encode()) % 10000
        
        # 组合: 机器码哈希(4位) + 过期天数(5位)
        code = f"{machine_hash:04d}{expiry_days:05d}"
        
        # 计算校验码
        checksum = calculate_checksum(code)
        
        # 返回10位激活码
        activation_code = code + checksum
        
        # 格式化为 XXXXX-XXXXX
        return f"{activation_code[:5]}-{activation_code[5:]}"
    except Exception as e:
        print(f"错误: {e}")
        return ""

def main():
    if len(sys.argv) < 2:
        print("使用方法: python3 generate_activation_10.py <机器码> [天数]")
        print("示例: python3 generate_activation_10.py A1B2C3D4E5F6G7H8 30")
        sys.exit(1)
    
    machine_code = sys.argv[1]
    days = int(sys.argv[2]) if len(sys.argv) > 2 else 30
    
    expiry_date = datetime.now() + timedelta(days=days)
    expiry_str = expiry_date.strftime("%Y-%m-%d %H:%M:%S")
    
    activation_code = generate_activation_code(machine_code, expiry_str)
    
    print("=" * 60)
    print("LeafStudio 激活码生成器 (10位版本)".center(60))
    print("=" * 60)
    print(f"\n机器码:     {machine_code}")
    print(f"有效期:     {days} 天")
    print(f"过期时间:   {expiry_str}")
    print(f"\n激活码:     {activation_code}  (10位)\n")
    print("=" * 60)

if __name__ == "__main__":
    main()
