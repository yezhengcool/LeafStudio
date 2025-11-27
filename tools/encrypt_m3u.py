#!/usr/bin/env python3
"""
M3U URL 加密工具
用于加密 M3U 文件中的播放地址，防止被抓包获取
"""

import base64
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad
import sys

# 密钥和 IV（必须与 App 中的 CryptoUtils.kt 一致）
SECRET_KEY = b'LeafStudio2024SecretKey12345'  # 32 bytes
IV = b'1234567890123456'  # 16 bytes

def encrypt_url(url):
    """加密 URL"""
    cipher = AES.new(SECRET_KEY, AES.MODE_CBC, IV)
    padded_data = pad(url.encode('utf-8'), AES.block_size)
    encrypted = cipher.encrypt(padded_data)
    return base64.b64encode(encrypted).decode('utf-8')

def decrypt_url(encrypted_url):
    """解密 URL（用于测试）"""
    cipher = AES.new(SECRET_KEY, AES.MODE_CBC, IV)
    encrypted_data = base64.b64decode(encrypted_url)
    decrypted = cipher.decrypt(encrypted_data)
    return unpad(decrypted, AES.block_size).decode('utf-8')

def process_m3u_file(input_file, output_file):
    """处理 M3U 文件，加密所有 URL"""
    with open(input_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    encrypted_lines = []
    for line in lines:
        line = line.strip()
        # 如果是 URL 行（包含 ://）
        if '://' in line and not line.startswith('#'):
            # 分离 URL 和可能的参数（|分隔）
            parts = line.split('|', 1)
            encrypted_url = encrypt_url(parts[0])
            if len(parts) > 1:
                encrypted_lines.append(f"{encrypted_url}|{parts[1]}\n")
            else:
                encrypted_lines.append(f"{encrypted_url}\n")
        else:
            encrypted_lines.append(line + '\n')
    
    with open(output_file, 'w', encoding='utf-8') as f:
        f.writelines(encrypted_lines)
    
    print(f"✅ 加密完成！")
    print(f"   输入文件: {input_file}")
    print(f"   输出文件: {output_file}")

def main():
    if len(sys.argv) < 2:
        print("用法:")
        print("  加密单个 URL:")
        print("    python3 encrypt_m3u.py <URL>")
        print("  加密整个 M3U 文件:")
        print("    python3 encrypt_m3u.py <input.m3u> <output.m3u>")
        print("\n示例:")
        print("    python3 encrypt_m3u.py http://example.com/stream.m3u8")
        print("    python3 encrypt_m3u.py channels.m3u channels_encrypted.m3u")
        sys.exit(1)
    
    if len(sys.argv) == 2:
        # 加密单个 URL
        url = sys.argv[1]
        encrypted = encrypt_url(url)
        print(f"原始 URL: {url}")
        print(f"加密后:   {encrypted}")
        print(f"\n验证解密: {decrypt_url(encrypted)}")
    else:
        # 加密整个文件
        input_file = sys.argv[1]
        output_file = sys.argv[2]
        process_m3u_file(input_file, output_file)

if __name__ == '__main__':
    main()
