#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从服务器收集加密网关部署文件并打包
"""

import paramiko
import os
import sys

# SSH 连接配置
hostname = '192.168.1.25'
username = 'root'
password = '123456'
port = 22

# 远程目录
remote_base = '/opt/publish-gateway'

# 本地输出目录
local_output = 'd:/project02/publish-gateway/deploy-package'

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

def collect_files():
    """收集服务器上的文件"""
    print(f"连接到服务器 {hostname}...")
    ssh = ssh_connect()
    
    try:
        # 1. 查看 Docker 镜像
        print("\n=== 检查 Docker 镜像 ===")
        output, error = execute_command(ssh, "docker images | grep gateway-udp-proxy")
        print(output if output else "未找到 gateway-udp-proxy 镜像")
        
        output, error = execute_command(ssh, "docker images | grep redis")
        print(output if output else "未找到 redis 镜像")
        
        # 2. 查看运行中的容器
        print("\n=== 检查运行中的容器 ===")
        output, error = execute_command(ssh, "docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}' | grep -E 'gateway-udp-proxy|publish-gateway|redis'")
        print(output if output else "未找到相关容器")
        
        # 3. 查看目录结构
        print("\n=== 目录结构 ===")
        output, error = execute_command(ssh, f"ls -la {remote_base}/")
        print(output)
        
        # 4. 查看 certs 目录
        print("\n=== certs/ 目录 ===")
        output, error = execute_command(ssh, f"ls -la {remote_base}/certs/")
        print(output)
        
        # 5. 查看 lib 目录
        print("\n=== lib/ 目录（关键文件）===")
        output, error = execute_command(ssh, f"ls -la {remote_base}/lib/ | grep -E 'libvauthsdk|libCommonLib|pack.svac|SkfUtility|SdfUtility'")
        print(output)
        
        # 6. 查看 trust 目录
        print("\n=== trust/ 目录 ===")
        output, error = execute_command(ssh, f"ls -la {remote_base}/trust/")
        print(output)
        
        print("\n文件收集完成！")
        
    finally:
        ssh.close()

if __name__ == "__main__":
    collect_files()
