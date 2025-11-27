#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
激活码生成工具
用于生成随机激活码并插入到数据库中
"""

import random
import string
import sys
import mysql.connector
from datetime import datetime

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'user': 'your_username',
    'password': 'your_password',
    'database': 'your_database'
}

def generate_activation_code(length=12):
    """
    生成随机激活码
    格式: XXXX-XXXX-XXXX (大写字母和数字组合，排除易混淆字符)
    """
    # 排除易混淆的字符: 0, O, I, 1, L
    chars = '23456789ABCDEFGHJKMNPQRSTUVWXYZ'
    
    code_parts = []
    for i in range(3):
        part = ''.join(random.choice(chars) for _ in range(4))
        code_parts.append(part)
    
    return '-'.join(code_parts)

def insert_activation_code(code, duration_days):
    """
    将激活码插入到数据库
    """
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        query = """
            INSERT INTO activation_codes (code, duration_days, is_used)
            VALUES (%s, %s, %s)
        """
        cursor.execute(query, (code, duration_days, False))
        conn.commit()
        
        cursor.close()
        conn.close()
        
        return True
    except Exception as e:
        print(f"数据库错误: {e}")
        return False

def batch_generate(count, duration_days):
    """
    批量生成激活码
    """
    print(f"开始生成 {count} 个激活码，每个有效期 {duration_days} 天")
    print("-" * 60)
    
    success_count = 0
    codes = []
    
    for i in range(count):
        # 生成唯一的激活码
        while True:
            code = generate_activation_code()
            if code not in codes:
                codes.append(code)
                break
        
        # 插入数据库
        if insert_activation_code(code, duration_days):
            success_count += 1
            print(f"{i+1}. {code} - {duration_days}天 ✓")
        else:
            print(f"{i+1}. {code} - 插入失败 ✗")
    
    print("-" * 60)
    print(f"生成完成: 成功 {success_count}/{count}")
    
    # 保存到文件
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"activation_codes_{timestamp}.txt"
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(f"激活码列表 - 生成时间: {datetime.now()}\n")
        f.write(f"有效期: {duration_days} 天\n")
        f.write("=" * 60 + "\n\n")
        for i, code in enumerate(codes, 1):
            f.write(f"{i}. {code}\n")
    
    print(f"激活码已保存到: {filename}")

def main():
    """
    主函数
    """
    print("=" * 60)
    print("激活码生成工具")
    print("=" * 60)
    
    try:
        count = int(input("请输入要生成的激活码数量: "))
        duration_days = int(input("请输入激活码有效天数: "))
        
        if count <= 0 or duration_days <= 0:
            print("错误: 数量和天数必须大于0")
            return
        
        confirm = input(f"\n确认生成 {count} 个 {duration_days} 天的激活码? (y/n): ")
        if confirm.lower() != 'y':
            print("已取消")
            return
        
        batch_generate(count, duration_days)
        
    except ValueError:
        print("错误: 请输入有效的数字")
    except KeyboardInterrupt:
        print("\n\n已取消")
    except Exception as e:
        print(f"错误: {e}")

if __name__ == "__main__":
    main()
