#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从服务器收集加密网关部署文件并打包成交付包
使用 paramiko 的 SFTP 下载文件，避免 scp 密码输入问题
"""

import paramiko
import os
import sys
from pathlib import Path

# SSH 连接配置
hostname = '192.168.1.25'
username = 'root'
password = '123456'
port = 22

# 远程目录
remote_base = '/opt/publish-gateway'

# 本地输出目录
local_output = 'd:/project02/publish-gateway/deploy-package'
images_dir = f'{local_output}/images'
lib_dir = f'{local_output}/lib'
certs_dir = f'{local_output}/certs'
trust_dir = f'{local_output}/trust'

def ssh_connect():
    """创建 SSH 连接"""
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(hostname, port, username, password, timeout=10)
    return ssh

def execute_command(ssh, command):
    """执行远程命令"""
    stdin, stdout, stderr = ssh.exec_command(command)
    return stdout.read().decode('utf-8'), stderr.read().decode('utf-8')

def create_local_dirs():
    """创建本地目录"""
    for d in [local_output, images_dir, lib_dir, certs_dir, trust_dir]:
        os.makedirs(d, exist_ok=True)
        print(f"创建目录: {d}")

def export_docker_images(ssh):
    """导出 Docker 镜像到远程临时目录"""
    print("\n=== 导出 Docker 镜像 ===")
    
    # 导出 gateway-udp-proxy:1.0.0
    print("导出 gateway-udp-proxy:1.0.0...")
    output, error = execute_command(ssh, "docker save gateway-udp-proxy:1.0.0 | gzip > /tmp/publish-gateway.tar.gz")
    if error:
        print(f"警告: {error}")
    
    # 导出 redis:7-alpine
    print("导出 redis:7-alpine...")
    output, error = execute_command(ssh, "docker save redis:7-alpine | gzip > /tmp/redis-7-alpine.tar.gz")
    if error:
        print(f"警告: {error}")
    
    # 检查导出文件
    output, error = execute_command(ssh, "ls -lh /tmp/*.tar.gz")
    print(f"导出的镜像文件:\n{output}")

def download_file_sftp(sftp, remote_path, local_path):
    """使用 SFTP 下载单个文件"""
    try:
        sftp.get(remote_path, local_path)
        file_size = os.path.getsize(local_path)
        print(f"  下载: {os.path.basename(local_path)} ({file_size:,} bytes)")
        return True
    except Exception as e:
        print(f"  跳过: {os.path.basename(local_path)} ({e})")
        return False

def download_files(sftp):
    """下载所有文件"""
    print("\n=== 下载文件 ===")
    
    # 下载 Docker 镜像
    print("\n下载 Docker 镜像:")
    download_file_sftp(sftp, "/tmp/publish-gateway.tar.gz", f"{images_dir}/publish-gateway.tar.gz")
    download_file_sftp(sftp, "/tmp/redis-7-alpine.tar.gz", f"{images_dir}/redis-7-alpine.tar.gz")
    
    # 下载 lib 目录的关键文件
    print("\n下载 lib/ 目录:")
    lib_files = [
        'libvauthsdk.so',
        'libCommonLib.so',
        'pack.svac',
        'SkfUtility.zl',
        'SdfUtility.zl',
        'libGmsslUtility.so',
        'libSKFInterface.so',
        'libzbaselib.so',
        'libzdevbase.so',
        'libZxTransRepackSDK.so',
        'libvauthpackbridge.so',
        'StreamAnalyzer.zl',
        'SVAC2Decoder.zl',
        'SVAC2Encoder.zl',
        'FFMPEGUtility.zl',
    ]
    
    for f in lib_files:
        remote_path = f"{remote_base}/lib/{f}"
        local_path = f"{lib_dir}/{f}"
        download_file_sftp(sftp, remote_path, local_path)
    
    # 下载 certs 目录
    print("\n下载 certs/ 目录:")
    cert_files = [
        '44010100003330003024_SIGN.cer',
        '44030000003330000305_SIGN.cer',
        '44030000003330000303_SIGN.cer',
    ]
    
    for f in cert_files:
        remote_path = f"{remote_base}/certs/{f}"
        local_path = f"{certs_dir}/{f}"
        download_file_sftp(sftp, remote_path, local_path)
    
    # 下载 trust 目录
    print("\n下载 trust/ 目录:")
    trust_files = ['spkg-prod-01.pub']
    for f in trust_files:
        remote_path = f"{remote_base}/trust/{f}"
        local_path = f"{trust_dir}/{f}"
        download_file_sftp(sftp, remote_path, local_path)

def clean_remote_temp(ssh):
    """清理远程临时文件"""
    print("\n=== 清理远程临时文件 ===")
    execute_command(ssh, "rm -f /tmp/*.tar.gz")
    print("清理完成")

def print_summary():
    """打印摘要"""
    print("\n" + "=" * 60)
    print("文件收集完成！")
    print("=" * 60)
    print(f"\n输出目录: {local_output}")
    
    # 列出下载的文件
    print("\n下载的文件:")
    for root, dirs, files in os.walk(local_output):
        for file in files:
            file_path = os.path.join(root, file)
            file_size = os.path.getsize(file_path)
            rel_path = os.path.relpath(file_path, local_output)
            print(f"  {rel_path} ({file_size:,} bytes)")

def main():
    print("开始收集加密网关部署文件...")
    print(f"服务器: {hostname}")
    print(f"输出目录: {local_output}")
    
    # 创建本地目录
    create_local_dirs()
    
    # 连接服务器
    ssh = ssh_connect()
    sftp = ssh.open_sftp()
    
    try:
        # 导出 Docker 镜像
        export_docker_images(ssh)
        
        # 下载文件
        download_files(sftp)
        
        # 清理远程临时文件
        clean_remote_temp(ssh)
        
    finally:
        sftp.close()
        ssh.close()
    
    # 打印摘要
    print_summary()

if __name__ == "__main__":
    main()
