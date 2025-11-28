#!/usr/bin/env python3
"""
会话管理功能测试脚本
测试 SSE 连接和消息请求的时序问题
"""

import requests
import json
import time
import threading
import sys

ROUTER_URL = "http://localhost:8052"
SERVICE_NAME = "mcp-server-v6"

def test_session_management():
    print("=" * 50)
    print("会话管理功能测试")
    print("=" * 50)
    print()
    
    # 1. 建立 SSE 连接
    print("1. 建立 SSE 连接...")
    sse_url = f"{ROUTER_URL}/sse?serviceName={SERVICE_NAME}"
    
    session_id = None
    endpoint_received = False
    
    def read_sse():
        nonlocal session_id, endpoint_received
        try:
            response = requests.get(sse_url, stream=True, timeout=5)
            if response.status_code == 200:
                print(f"✅ SSE 连接已建立 (状态码: {response.status_code})")
                for line in response.iter_lines(decode_unicode=True):
                    if line:
                        print(f"   SSE 事件: {line[:100]}...")
                        if "event:endpoint" in line or "data:" in line:
                            # 尝试从 endpoint 事件中提取 sessionId
                            if "sessionId=" in line:
                                import re
                                match = re.search(r'sessionId=([^"&\s]+)', line)
                                if match:
                                    session_id = match.group(1)
                                    print(f"✅ 提取到 sessionId: {session_id}")
                                    endpoint_received = True
                                    break
            else:
                print(f"❌ SSE 连接失败 (状态码: {response.status_code})")
        except Exception as e:
            print(f"❌ SSE 连接异常: {e}")
    
    # 在后台线程中读取 SSE
    sse_thread = threading.Thread(target=read_sse, daemon=True)
    sse_thread.start()
    
    # 等待 endpoint 事件
    timeout = 5
    start_time = time.time()
    while not endpoint_received and (time.time() - start_time) < timeout:
        time.sleep(0.1)
    
    if not session_id:
        print("❌ 无法从 SSE 响应中提取 sessionId")
        return False
    
    print()
    
    # 等待一下，确保 SSE 连接完全建立
    print("2. 等待 SSE 连接完全建立...")
    time.sleep(2)
    print("✅ 等待完成")
    print()
    
    # 3. 发送 initialize 请求
    print("3. 发送 initialize 请求...")
    init_request = {
        "jsonrpc": "2.0",
        "id": "test-init-1",
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {
                "name": "test-client",
                "version": "1.0.0"
            }
        }
    }
    
    message_url = f"{ROUTER_URL}/mcp/{SERVICE_NAME}/message?sessionId={session_id}"
    
    try:
        response = requests.post(
            message_url,
            json=init_request,
            headers={"Content-Type": "application/json"},
            timeout=10
        )
        
        print(f"   响应状态码: {response.status_code}")
        print(f"   响应内容: {response.text[:200]}")
        
        if response.status_code == 202 or "accepted" in response.text.lower():
            print("✅ initialize 请求已接受，响应将通过 SSE 发送")
        else:
            print("⚠️  initialize 请求响应异常")
    except Exception as e:
        print(f"❌ 发送请求失败: {e}")
        return False
    
    print()
    
    # 4. 等待响应通过 SSE 到达
    print("4. 等待响应通过 SSE 到达...")
    time.sleep(3)
    print("✅ 等待完成")
    print()
    
    # 5. 检查日志
    print("5. 检查日志中的会话管理信息...")
    log_file = "/Users/shine/projects.mcp-router-sse-parent/mcp-router-v3/mcp-router-v3-session-test.log"
    
    try:
        with open(log_file, 'r', encoding='utf-8') as f:
            log_content = f.read()
        
        # 检查错误
        no_sink_errors = log_content.count("No SSE sink found") + log_content.count("SSE sink not found")
        if no_sink_errors > 0:
            print(f"❌ 发现 {no_sink_errors} 条 'No SSE sink found' 错误")
            # 查找最近的错误
            lines = log_content.split('\n')
            for i, line in enumerate(lines):
                if "No SSE sink found" in line or "SSE sink not found" in line:
                    print(f"   错误: {line[:150]}")
                    if i < len(lines) - 1:
                        print(f"   上下文: {lines[i+1][:150]}")
                    break
        else:
            print("✅ 未发现 'No SSE sink found' 错误")
        
        # 检查成功日志
        sink_found = log_content.count("SSE sink found") + log_content.count("Successfully sent.*via SSE")
        if sink_found > 0:
            print(f"✅ 发现 SSE sink 成功使用的日志")
            # 查找最近的成功日志
            lines = log_content.split('\n')
            for line in reversed(lines):
                if "SSE sink found" in line or "Successfully sent" in line and "via SSE" in line:
                    print(f"   成功: {line[:150]}")
                    break
        
        # 检查已注册的会话
        registered_sessions = [line for line in lines if "Registered SSE sink" in line or "Registered client session" in line]
        if registered_sessions:
            print(f"✅ 发现 {len(registered_sessions)} 条会话注册日志")
            for line in registered_sessions[-3:]:
                print(f"   {line[:150]}")
        
    except FileNotFoundError:
        print(f"⚠️  日志文件不存在: {log_file}")
    except Exception as e:
        print(f"⚠️  读取日志文件失败: {e}")
    
    print()
    print("=" * 50)
    print("测试完成")
    print("=" * 50)
    
    return no_sink_errors == 0

if __name__ == "__main__":
    success = test_session_management()
    sys.exit(0 if success else 1)




