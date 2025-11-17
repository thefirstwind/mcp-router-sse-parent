#!/usr/bin/env python3
"""
SSE连接测试脚本
测试SSE连接是否能保持10分钟不断开，并验证心跳机制
"""

import requests
import time
import json
from datetime import datetime
from typing import Optional

class SseConnectionTest:
    def __init__(self, base_url: str = "http://127.0.0.1:8052"):
        self.base_url = base_url
        self.endpoint = f"{base_url}/sse/mcp-server-v6"
        self.session = requests.Session()
        self.connected = False
        self.last_heartbeat = None
        self.message_count = 0
        self.start_time = None
        self.end_time = None
        
    def test_connection(self, duration_seconds: int = 600) -> bool:
        """
        测试SSE连接
        :param duration_seconds: 测试持续时间（秒），默认600秒（10分钟）
        :return: 是否成功保持连接
        """
        print(f"开始测试SSE连接: {self.endpoint}")
        print(f"目标持续时间: {duration_seconds}秒 ({duration_seconds/60:.1f}分钟)")
        print("-" * 60)
        
        self.start_time = time.time()
        target_end_time = self.start_time + duration_seconds
        
        try:
            # 建立SSE连接
            response = self.session.get(
                self.endpoint,
                stream=True,
                headers={
                    'Accept': 'text/event-stream',
                    'Cache-Control': 'no-cache',
                },
                timeout=None  # 不设置超时
            )
            
            if response.status_code != 200:
                print(f"❌ 连接失败: HTTP {response.status_code}")
                print(f"响应内容: {response.text}")
                return False
            
            print(f"✅ 连接成功: HTTP {response.status_code}")
            print(f"响应头: {dict(response.headers)}")
            print("-" * 60)
            
            self.connected = True
            buffer = ""
            
            # 读取SSE事件流
            for line in response.iter_lines(decode_unicode=True):
                if not self.connected:
                    break
                    
                current_time = time.time()
                
                # 检查是否达到目标时间
                if current_time >= target_end_time:
                    print(f"\n✅ 成功保持连接 {duration_seconds}秒 ({duration_seconds/60:.1f}分钟)")
                    self.end_time = current_time
                    break
                
                if line:
                    buffer += line + "\n"
                elif buffer.strip():
                    # 空行表示一个完整的事件
                    self._process_event(buffer.strip())
                    buffer = ""
                
                # 每30秒输出一次状态
                elapsed = current_time - self.start_time
                if int(elapsed) % 30 == 0 and int(elapsed) > 0:
                    self._print_status(elapsed)
            
            # 处理剩余缓冲区
            if buffer.strip():
                self._process_event(buffer.strip())
                
        except requests.exceptions.RequestException as e:
            elapsed = time.time() - self.start_time if self.start_time else 0
            print(f"\n❌ 连接异常 (已持续 {elapsed:.1f}秒): {e}")
            self.end_time = time.time()
            return False
        except KeyboardInterrupt:
            elapsed = time.time() - self.start_time if self.start_time else 0
            print(f"\n⚠️  测试被用户中断 (已持续 {elapsed:.1f}秒)")
            self.end_time = time.time()
            return False
        finally:
            self.session.close()
            
        return self._print_summary()
    
    def _process_event(self, event_data: str):
        """处理SSE事件"""
        self.message_count += 1
        
        # 解析SSE事件
        lines = event_data.split('\n')
        event_type = None
        data = None
        event_id = None
        
        for line in lines:
            if line.startswith('event:'):
                event_type = line[6:].strip()
            elif line.startswith('data:'):
                data = line[5:].strip()
            elif line.startswith('id:'):
                event_id = line[3:].strip()
        
        # 处理心跳事件
        if event_type == 'heartbeat':
            self.last_heartbeat = time.time()
            try:
                if data:
                    heartbeat_data = json.loads(data)
                    timestamp = heartbeat_data.get('timestamp', '')
                    print(f"💓 收到心跳 [{timestamp}] (消息 #{self.message_count})")
            except json.JSONDecodeError:
                print(f"💓 收到心跳 (消息 #{self.message_count}, 数据解析失败)")
        elif event_type == 'connected':
            print(f"🔌 连接确认事件 (消息 #{self.message_count})")
            if data:
                try:
                    conn_data = json.loads(data)
                    print(f"   会话ID: {conn_data.get('sessionId', 'N/A')}")
                    print(f"   客户端ID: {conn_data.get('clientId', 'N/A')}")
                except json.JSONDecodeError:
                    pass
        else:
            print(f"📨 收到事件: {event_type or 'message'} (消息 #{self.message_count})")
            if data:
                print(f"   数据: {data[:100]}..." if len(data) > 100 else f"   数据: {data}")
    
    def _print_status(self, elapsed: float):
        """打印当前状态"""
        minutes = int(elapsed // 60)
        seconds = int(elapsed % 60)
        heartbeat_info = ""
        if self.last_heartbeat:
            heartbeat_elapsed = time.time() - self.last_heartbeat
            heartbeat_info = f", 上次心跳: {heartbeat_elapsed:.1f}秒前"
        
        print(f"⏱️  状态更新: 已运行 {minutes}分{seconds}秒, "
              f"收到 {self.message_count} 条消息{heartbeat_info}")
    
    def _print_summary(self) -> bool:
        """打印测试总结"""
        if not self.start_time or not self.end_time:
            return False
            
        duration = self.end_time - self.start_time
        minutes = int(duration // 60)
        seconds = int(duration % 60)
        
        print("\n" + "=" * 60)
        print("测试总结")
        print("=" * 60)
        print(f"连接端点: {self.endpoint}")
        print(f"持续时间: {minutes}分{seconds}秒 ({duration:.1f}秒)")
        print(f"收到消息数: {self.message_count}")
        print(f"最后心跳: {time.time() - self.last_heartbeat:.1f}秒前" if self.last_heartbeat else "最后心跳: 无")
        print(f"连接状态: {'✅ 保持连接' if self.connected else '❌ 已断开'}")
        
        # 判断是否成功
        success = duration >= 600 and self.connected  # 至少10分钟
        if success:
            print("\n✅ 测试通过: 成功保持连接10分钟以上")
        else:
            print(f"\n❌ 测试失败: 连接持续时间不足或已断开")
        
        return success


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description='测试SSE连接保持能力')
    parser.add_argument('--url', default='http://127.0.0.1:8052',
                       help='服务器URL (默认: http://127.0.0.1:8052)')
    parser.add_argument('--duration', type=int, default=600,
                       help='测试持续时间（秒，默认600秒=10分钟）')
    parser.add_argument('--quick', action='store_true',
                       help='快速测试模式（60秒）')
    
    args = parser.parse_args()
    
    duration = 60 if args.quick else args.duration
    
    test = SseConnectionTest(base_url=args.url)
    success = test.test_connection(duration_seconds=duration)
    
    exit(0 if success else 1)


if __name__ == '__main__':
    main()

