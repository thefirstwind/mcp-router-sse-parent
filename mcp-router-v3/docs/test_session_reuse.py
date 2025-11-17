#!/usr/bin/env python3
"""
测试脚本：验证 SSE 连接创建后，使用同一个 sessionId 发送多个请求

测试目标：
1. 创建 SSE 连接，获取 sessionId
2. 使用同一个 sessionId 发送多个不同的请求（tools/list, resources/list, prompts/list 等）
3. 验证所有请求都能正确路由到同一个后端服务
"""

import requests
import json
import re
import sys
import threading
import time
from typing import Optional, Dict, Any

BASE_URL = "http://localhost:8052"
SERVICE_NAME = "mcp-server-v6"

# ANSI 颜色代码
class Colors:
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    NC = '\033[0m'  # No Color


def print_colored(message: str, color: str = Colors.NC):
    """打印带颜色的消息"""
    print(f"{color}{message}{Colors.NC}")


# 全局变量：保持 SSE 连接打开
_sse_response = None
_sse_thread = None
_sse_keep_alive = True

def keep_sse_alive(response):
    """在后台线程中保持 SSE 连接打开，读取心跳消息"""
    global _sse_keep_alive
    try:
        for line in response.iter_lines(decode_unicode=True):
            if not _sse_keep_alive:
                break
            if line:
                # 只打印心跳消息（可选）
                if "heartbeat" in line.lower() or line.startswith(":"):
                    pass  # 忽略心跳消息
    except Exception as e:
        if _sse_keep_alive:
            print_colored(f"  ⚠️ SSE 连接读取异常: {e}", Colors.YELLOW)
    finally:
        try:
            response.close()
        except:
            pass

def extract_session_id_from_sse(sse_url: str) -> Optional[str]:
    """
    从 SSE 连接中提取 sessionId，并保持连接打开
    
    SSE 响应格式：
    event:endpoint
    data:http://localhost:8052/mcp/message?sessionId=xxx
    """
    global _sse_response, _sse_thread, _sse_keep_alive
    
    try:
        print_colored(f"📡 正在连接到: {sse_url}", Colors.YELLOW)
        print("   等待服务器响应...")
        
        response = requests.get(
            sse_url,
            headers={"Accept": "text/event-stream"},
            stream=True,
            timeout=5
        )
        response.raise_for_status()
        print_colored("   ✅ HTTP 连接已建立", Colors.GREEN)
        
        # 保存 response 以便后续保持连接
        _sse_response = response
        _sse_keep_alive = True
        
        # 读取前几行来获取 endpoint 信息
        lines_read = 0
        endpoint_url = None
        print("   📥 正在读取 SSE 流数据...")
        
        for line in response.iter_lines(decode_unicode=True):
            if line is None:
                continue
                
            lines_read += 1
            print(f"   [行 {lines_read}] {line}")
            
            # 查找 data: 行（包含 endpoint URL）
            if line.startswith("data:"):
                endpoint_url = line[5:].strip()  # 移除 "data:" 前缀
                print_colored(f"   ✅ 找到 endpoint URL", Colors.GREEN)
                break
            
            # 限制读取行数，避免无限等待
            if lines_read >= 10:
                print_colored(f"   ⚠️ 已读取 {lines_read} 行，停止读取", Colors.YELLOW)
                break
        
        # 不关闭连接！在后台线程中保持连接打开
        _sse_thread = threading.Thread(target=keep_sse_alive, args=(response,), daemon=True)
        _sse_thread.start()
        print_colored("  ✅ SSE 连接已建立并保持打开", Colors.GREEN)
        
        if not endpoint_url:
            print_colored("❌ 无法从 SSE 响应中提取 endpoint URL", Colors.RED)
            return None
        
        # 从 endpoint URL 中提取 sessionId
        # 格式：http://localhost:8052/mcp/message?sessionId=xxx
        match = re.search(r'sessionId=([^&\s]+)', endpoint_url)
        if match:
            session_id = match.group(1)
            print_colored(f"✅ 成功提取 sessionId: {session_id}", Colors.GREEN)
            print(f"  Endpoint URL: {endpoint_url}")
            return session_id
        else:
            print_colored(f"❌ 无法从 endpoint URL 中提取 sessionId: {endpoint_url}", Colors.RED)
            return None
            
    except requests.exceptions.RequestException as e:
        print_colored(f"❌ SSE 连接失败: {e}", Colors.RED)
        return None

def close_sse_connection():
    """关闭 SSE 连接"""
    global _sse_response, _sse_thread, _sse_keep_alive
    _sse_keep_alive = False
    if _sse_response:
        try:
            _sse_response.close()
        except:
            pass
        _sse_response = None
    if _sse_thread:
        _sse_thread.join(timeout=2)
        _sse_thread = None


def send_mcp_request(method: str, params: Dict[str, Any], request_id: str, 
                     endpoint: str, max_retries: int = 0, service_name: str = None):
    """
    发送 MCP 请求（支持重试）
    
    Args:
        method: MCP 方法名
        params: 请求参数
        request_id: 请求ID
        endpoint: 端点URL
        max_retries: 最大重试次数（默认0，不重试）
    
    Returns:
        (success: bool, response: dict or None)
    """
    request_body = {
        "jsonrpc": "2.0",
        "id": request_id,
        "method": method,
        "params": params
    }
    
    for attempt in range(max_retries + 1):
        if attempt > 0:
            print_colored(f"   🔄 重试第 {attempt} 次...", Colors.YELLOW)
            time.sleep(1)  # 重试前等待1秒
        
        print_colored(f"\n📤 发送请求: {method} (id: {request_id})", Colors.BLUE)
        if attempt > 0:
            print(f"   [重试 {attempt}/{max_retries}]")
        
        # 如果提供了 service_name，确保 URL 中包含该参数
        request_url = endpoint
        if service_name and "serviceName=" not in endpoint:
            separator = "&" if "?" in endpoint else "?"
            request_url = f"{endpoint}{separator}serviceName={service_name}"
        
        print(f"   Endpoint: {request_url}")
        print(f"   Request: {json.dumps(request_body, indent=2, ensure_ascii=False)}")
        print("   ⏳ 等待服务器响应...")
        
        try:
            start_time = time.time()
            response = requests.post(
                request_url,
                json=request_body,
                headers={"Content-Type": "application/json"},
                timeout=70  # 增加超时时间到70秒，以匹配服务器端的60秒超时设置
            )
            elapsed_time = time.time() - start_time
            print_colored(f"   ✅ 收到响应 (耗时: {elapsed_time:.2f}秒)", Colors.GREEN)
            
            # 先读取响应内容，即使状态码不是 200
            response_text = response.text
            print(f"   📥 Response Status: {response.status_code}")
            print(f"   📥 Response Text (前500字符): {response_text[:500]}")
            
            # 如果状态码不是 200，记录错误但继续处理
            if response.status_code != 200:
                print_colored(f"   ⚠️ HTTP 状态码: {response.status_code}", Colors.YELLOW)
            
            try:
                result = response.json()
                print(f"   📥 Response JSON: {json.dumps(result, indent=2, ensure_ascii=False)}")
            except json.JSONDecodeError:
                print(f"   📥 Response (非JSON): {response_text[:500]}")
                result = {"error": {"message": f"Non-JSON response (status {response.status_code}): {response_text[:200]}"}}
            
            # 检查状态码，如果是错误状态码，标记为失败
            if response.status_code != 200:
                error_message = result.get("error", {}).get("message", f"HTTP {response.status_code}") if isinstance(result, dict) else f"HTTP {response.status_code}"
                if attempt < max_retries:
                    print_colored(f"   ⚠️ 请求失败，将重试...", Colors.YELLOW)
                    continue
                print_colored("   ❌ 请求失败", Colors.RED)
                return False, result
            
            response.raise_for_status()
            
            # 检查响应
            has_error = "error" in result
            has_result = "result" in result
            
            # 尝试提取路由信息
            target_service = "unknown"
            if "result" in result:
                if isinstance(result["result"], dict):
                    target_service = result["result"].get("targetService") or \
                                   result["result"].get("metadata", {}).get("targetServer") or \
                                   "unknown"
            
            if has_error:
                error_info = result.get("error", {})
                error_message = error_info.get('message', 'Unknown error')
                
                # 如果是超时错误且还有重试机会，则重试
                if "timeout" in error_message.lower() or "27000ms" in error_message or "Did not observe" in error_message:
                    if attempt < max_retries:
                        print_colored(f"   ⚠️ 请求超时，将重试...", Colors.YELLOW)
                        continue
                
                print_colored("   ❌ 请求失败", Colors.RED)
                print(f"   错误信息: {error_message}")
                return False, result
            else:
                print_colored("   ✅ 请求成功", Colors.GREEN)
                if target_service != "unknown":
                    print(f"   🎯 路由到的服务: {target_service}")
                return True, result
                
        except requests.exceptions.Timeout as e:
            if attempt < max_retries:
                print_colored(f"   ⚠️ 请求超时，将重试...", Colors.YELLOW)
                continue
            print_colored(f"   ❌ 请求超时 (超过70秒): {e}", Colors.RED)
            return False, None
        except requests.exceptions.HTTPError as e:
            # 尝试读取错误响应内容
            try:
                error_text = e.response.text if hasattr(e, 'response') and e.response else str(e)
                print_colored(f"   ❌ HTTP 错误: {e}", Colors.RED)
                print(f"   错误响应内容: {error_text[:500]}")
            except:
                print_colored(f"   ❌ HTTP 错误: {e}", Colors.RED)
            if attempt < max_retries:
                print_colored(f"   ⚠️ 请求异常，将重试...", Colors.YELLOW)
                time.sleep(1)
                continue
            return False, None
        except requests.exceptions.RequestException as e:
            if attempt < max_retries:
                print_colored(f"   ⚠️ 请求异常，将重试...", Colors.YELLOW)
                time.sleep(1)
                continue
            print_colored(f"   ❌ 请求异常: {e}", Colors.RED)
            return False, None
        except json.JSONDecodeError as e:
            print_colored(f"   ❌ JSON 解析失败: {e}", Colors.RED)
            print(f"   Response text: {response.text[:200]}")
            return False, None
    
    # 所有重试都失败了
    return False, None


def main():
    print("=" * 60)
    print_colored("🧪 SSE Session Reuse 验证测试", Colors.BLUE)
    print("=" * 60)
    print()
    print("📝 测试目标：验证 SSE 连接创建后，使用同一个 sessionId 发送多个请求")
    print(f"🌐 服务器地址: {BASE_URL}")
    print(f"🔧 服务名称: {SERVICE_NAME}")
    print()
    
    # 步骤1: 创建 SSE 连接并获取 sessionId
    print("=" * 60)
    print_colored("📡 步骤 1: 创建 SSE 连接...", Colors.YELLOW)
    print("=" * 60)
    sse_url = f"{BASE_URL}/sse/{SERVICE_NAME}"
    
    session_id = extract_session_id_from_sse(sse_url)
    if not session_id:
        print_colored("❌ 无法获取 sessionId，测试终止", Colors.RED)
        sys.exit(1)
    
    print()
    
    # 使用 /mcp/message?sessionId=xxx&serviceName=xxx 端点（包含 serviceName 参数）
    message_endpoint = f"{BASE_URL}/mcp/message?sessionId={session_id}&serviceName={SERVICE_NAME}"
    
    print("=" * 60)
    print_colored("🔥 步骤 2: 预热连接（发送一个简单请求）", Colors.YELLOW)
    print("=" * 60)
    print("💡 目的：预热后端连接，避免第一个复杂请求超时")
    print()
    
    # 预热请求：使用一个简单的请求来预热连接
    warmup_success, _ = send_mcp_request("resources/list", {}, "warmup-001", message_endpoint, max_retries=1, service_name=SERVICE_NAME)
    if warmup_success:
        print_colored("   ✅ 连接预热成功", Colors.GREEN)
    else:
        print_colored("   ⚠️ 连接预热失败，但继续测试", Colors.YELLOW)
    print()
    time.sleep(1)  # 等待连接稳定
    
    print("=" * 60)
    print_colored("📨 步骤 3: 使用同一个 sessionId 发送多个请求", Colors.BLUE)
    print("=" * 60)
    print(f"🔑 Session ID: {session_id}")
    print(f"📍 Message Endpoint: {message_endpoint}")
    print()
    
    # 测试用例列表
    test_cases = [
        ("tools/list", {}, "req-tools-001", 2),  # 第一个 tools/list 允许重试2次
        ("resources/list", {}, "req-resources-001", 0),
        ("prompts/list", {}, "req-prompts-001", 0),
        ("resources/templates/list", {}, "req-templates-001", 0),
        ("tools/list", {}, "req-tools-002", 0),  # 再次测试 tools/list，验证会话复用
    ]
    
    results = []
    total_tests = len(test_cases)
    for idx, (method, params, request_id, max_retries) in enumerate(test_cases, 1):
        print_colored(f"📋 测试 {idx}/{total_tests}: {method}", Colors.BLUE)
        success, response = send_mcp_request(method, params, request_id, message_endpoint, max_retries=max_retries, service_name=SERVICE_NAME)
        results.append((method, request_id, success, response))
        if success:
            print_colored(f"   ✅ 测试 {idx} 完成", Colors.GREEN)
        else:
            print_colored(f"   ❌ 测试 {idx} 失败", Colors.RED)
        print()
        time.sleep(0.5)  # 短暂延迟，避免请求过快
    
    # 总结
    print("=" * 60)
    print_colored("📊 测试总结", Colors.BLUE)
    print("=" * 60)
    print()
    print(f"🔑 Session ID: {session_id}")
    print(f"🔧 Service Name: {SERVICE_NAME}")
    print()
    print("📋 测试结果:")
    
    passed = 0
    total = len(results)
    
    for method, request_id, success, response in results:
        status = "✅ 通过" if success else "❌ 失败"
        color = Colors.GREEN if success else Colors.RED
        print_colored(f"   {method} ({request_id}): {status}", color)
        if success:
            passed += 1
    
    print()
    print(f"📈 总计: {passed}/{total} 测试通过 ({passed*100//total if total > 0 else 0}%)")
    
    # 关闭 SSE 连接
    print()
    print_colored("🔌 关闭 SSE 连接...", Colors.YELLOW)
    close_sse_connection()
    time.sleep(0.5)  # 等待连接关闭
    print_colored("   ✅ SSE 连接已关闭", Colors.GREEN)
    
    if passed == total:
        print_colored("✅ 所有测试通过！SSE 会话复用功能正常", Colors.GREEN)
        sys.exit(0)
    else:
        print_colored("❌ 部分测试失败", Colors.RED)
        sys.exit(1)


if __name__ == "__main__":
    main()





