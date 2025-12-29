# MCP Router v3 UI 改进实施指南

## 📋 概述

本指南提供了详细的步骤和代码片段，用于将 UI 改进方案应用到 `src/main/resources/static/admin/index.html` 文件中。

## 🎯 改进内容

1. 添加协议概览页面
2. 增强 SSE 会话页面（添加协议标识）
3. 增强 RESTful 请求页面（添加协议标识）
4. 添加 JSON 语法高亮样式

## 📝 实施步骤

### 步骤 1: 添加 CSS 样式

在 `</style>` 标签之前（约第 263 行），添加以下 CSS：

```css
        .protocol-card {
            background: #fff;
            border-radius: 12px;
            padding: 20px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.05);
            transition: transform 0.2s, box-shadow 0.2s;
        }
        .protocol-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        }
        .protocol-badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 600;
            color: white;
        }
        code {
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
            background: #f3f4f6;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 13px;
        }
        .json-key { color: #60a5fa; }
        .json-string { color: #34d399; }
        .json-number { color: #fbbf24; }
        .json-boolean { color: #a78bfa; }
        .json-null { color: #9ca3af; }
        .json-comment { color: #9ca3af; font-style: italic; }
```

### 步骤 2: 添加协议概览标签页

找到以下代码（约第 296-299 行）：
```html
<div class="tabs">
    <button class="tab active" data-tab="sse">SSE 会话</button>
    <button class="tab" data-tab="restful">RESTful 请求</button>
</div>
```

替换为：
```html
<div class="tabs">
    <button class="tab active" data-tab="protocol">协议概览</button>
    <button class="tab" data-tab="sse">SSE 会话</button>
    <button class="tab" data-tab="restful">RESTful 请求</button>
</div>
```

### 步骤 3: 添加协议概览页面内容

在 `<div class="tab-content active" id="sse-tab">` 之前（约第 301 行），添加以下内容：

```html
<div class="tab-content active" id="protocol-tab">
    <div class="card" style="margin-bottom: 24px;">
        <h2 style="margin-top: 0;">MCP 标准协议概览</h2>
        <div class="muted" style="margin-bottom: 20px;">
            MCP Router v3 支持三种传输协议，均基于 JSON-RPC 2.0 标准协议
        </div>
        
        <!-- 协议对比卡片 -->
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin-bottom: 32px;">
            <!-- RESTful 协议卡片 -->
            <div class="protocol-card" style="border-left: 4px solid #2563eb;">
                <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px;">
                    <span class="protocol-badge" style="background: #2563eb;">RESTful</span>
                    <span style="font-size: 14px; color: #6b7280;">同步请求/响应</span>
                </div>
                <div style="margin-bottom: 12px;">
                    <div class="muted" style="font-size: 12px; margin-bottom: 4px;">端点</div>
                    <code>POST /mcp/router/route/{serviceName}</code>
                </div>
                <div style="margin-bottom: 12px;">
                    <div class="muted" style="font-size: 12px; margin-bottom: 4px;">Content-Type</div>
                    <code>application/json</code>
                </div>
                <div style="margin-bottom: 12px;">
                    <div class="muted" style="font-size: 12px; margin-bottom: 4px;">特点</div>
                    <ul style="margin: 0; padding-left: 20px; font-size: 13px; color: #374151;">
                        <li>同步响应，无需会话</li>
                        <li>无状态，每次请求独立</li>
                        <li>适合一次性调用</li>
                    </ul>
                </div>
            </div>
            
            <!-- SSE 协议卡片 -->
            <div class="protocol-card" style="border-left: 4px solid #10b981;">
                <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px;">
                    <span class="protocol-badge" style="background: #10b981;">SSE</span>
                    <span style="font-size: 14px; color: #6b7280;">Server-Sent Events</span>
                </div>
                <div style="margin-bottom: 12px;">
                    <div class="muted" style="font-size: 12px; margin-bottom: 4px;">端点</div>
                    <code>GET /sse/{serviceName}</code>
                </div>
                <div style="margin-bottom: 12px;">
                    <div class="muted" style="font-size: 12px; margin-bottom: 4px;">Content-Type</div>
                    <code>text/event-stream</code>
                </div>
                <div style="margin-bottom: 12px;">
                    <div class="muted" style="font-size: 12px; margin-bottom: 4px;">特点</div>
                    <ul style="margin: 0; padding-left: 20px; font-size: 13px; color: #374151;">
                        <li>长连接，事件流</li>
                        <li>心跳间隔: 30秒</li>
                        <li>适合实时通信</li>
                    </ul>
                </div>
            </div>
            
            <!-- Streamable 协议卡片 -->
            <div class="protocol-card" style="border-left: 4px solid #8b5cf6;">
                <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px;">
                    <span class="protocol-badge" style="background: #8b5cf6;">Streamable</span>
                    <span style="font-size: 14px; color: #6b7280;">NDJSON 流式响应</span>
                </div>
                <div style="margin-bottom: 12px;">
                    <div class="muted" style="font-size: 12px; margin-bottom: 4px;">端点</div>
                    <code>GET /mcp/{serviceName}</code>
                </div>
                <div style="margin-bottom: 12px;">
                    <div class="muted" style="font-size: 12px; margin-bottom: 4px;">Content-Type</div>
                    <code>application/x-ndjson</code>
                </div>
                <div style="margin-bottom: 12px;">
                    <div class="muted" style="font-size: 12px; margin-bottom: 4px;">特点</div>
                    <ul style="margin: 0; padding-left: 20px; font-size: 13px; color: #374151;">
                        <li>NDJSON 流式响应</li>
                        <li>实时流，逐行 JSON</li>
                        <li>适合流式处理</li>
                    </ul>
                </div>
            </div>
        </div>
        
        <!-- JSON-RPC 2.0 格式说明 -->
        <div class="card" style="background: #f9fafb; border: 1px solid #e5e7eb; margin-bottom: 24px;">
            <h3 style="margin-top: 0;">JSON-RPC 2.0 标准格式</h3>
            <div class="muted" style="margin-bottom: 16px;">
                所有 MCP 消息均遵循 JSON-RPC 2.0 标准协议格式
            </div>
            <div style="background: #0f172a; border-radius: 8px; padding: 20px; margin-bottom: 16px;">
                <pre style="margin: 0; color: #f8fafc; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 13px; line-height: 1.6;">
{
  <span class="json-key">"jsonrpc"</span>: <span class="json-string">"2.0"</span>,        <span class="json-comment">← 协议版本（必需）</span>
  <span class="json-key">"method"</span>: <span class="json-string">"tools/call"</span>,  <span class="json-comment">← MCP 方法（必需）</span>
  <span class="json-key">"id"</span>: <span class="json-string">"req-001"</span>,      <span class="json-comment">← 请求 ID（必需）</span>
  <span class="json-key">"params"</span>: {              <span class="json-comment">← 参数（可选）</span>
    <span class="json-key">"name"</span>: <span class="json-string">"getPersonById"</span>,
    <span class="json-key">"arguments"</span>: {
      <span class="json-key">"id"</span>: <span class="json-number">1</span>
    }
  }
}</pre>
            </div>
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px;">
                <div>
                    <div style="font-weight: 600; margin-bottom: 8px; color: #374151;">MCP 标准方法分类</div>
                    <div style="font-size: 13px; color: #6b7280;">
                        <div>• <strong>tools</strong>: list, call</div>
                        <div>• <strong>resources</strong>: list, read</div>
                        <div>• <strong>prompts</strong>: list, get</div>
                        <div>• <strong>initialize</strong></div>
                    </div>
                </div>
                <div>
                    <div style="font-weight: 600; margin-bottom: 8px; color: #374151;">传输协议</div>
                    <div style="font-size: 13px; color: #6b7280;">
                        <div>• RESTful (HTTP POST)</div>
                        <div>• SSE (Server-Sent Events)</div>
                        <div>• Streamable (NDJSON)</div>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- 协议流程图 -->
        <div class="card">
            <h3 style="margin-top: 0;">协议工作流程</h3>
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 24px; margin-top: 20px;">
                <div>
                    <div style="font-weight: 600; margin-bottom: 12px; color: #2563eb;">RESTful 流程</div>
                    <div style="font-size: 13px; color: #6b7280; line-height: 1.8;">
                        <div>1. Client → POST /route/{svc}</div>
                        <div>2. Router → 路由到后端服务</div>
                        <div>3. Server → 返回 JSON-RPC 响应</div>
                        <div>4. Router → 返回给 Client</div>
                    </div>
                </div>
                <div>
                    <div style="font-weight: 600; margin-bottom: 12px; color: #10b981;">SSE 流程</div>
                    <div style="font-size: 13px; color: #6b7280; line-height: 1.8;">
                        <div>1. Client → GET /sse/{svc}</div>
                        <div>2. Router → 建立 SSE 连接</div>
                        <div>3. Client → POST /mcp/message</div>
                        <div>4. Router → SSE 事件流返回</div>
                    </div>
                </div>
                <div>
                    <div style="font-weight: 600; margin-bottom: 12px; color: #8b5cf6;">Streamable 流程</div>
                    <div style="font-size: 13px; color: #6b7280; line-height: 1.8;">
                        <div>1. Client → GET /mcp/{svc}</div>
                        <div>2. Router → 建立 NDJSON 流</div>
                        <div>3. Client → POST /mcp/message</div>
                        <div>4. Router → NDJSON 流返回</div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
```

### 步骤 4: 修改 SSE 会话列表显示

找到 `renderSessions` 函数中的以下代码（约第 590 行）：
```javascript
tbody.innerHTML = filtered.map(s => `
    <tr data-session-id="${s.sessionId}">
        <td>${s.sessionId}</td>
        <td>${s.serviceName ?? '未绑定'}</td>
        <td><span class="badge ${s.active ? 'success' : ''}">${s.active ? 'Active' : 'Closed'}</span></td>
        <td>${formatTime(s.lastActive)}</td>
    </tr>
`).join('');
```

替换为：
```javascript
tbody.innerHTML = filtered.map(s => `
    <tr data-session-id="${s.sessionId}">
        <td>${s.sessionId}</td>
        <td>
            <div style="display: flex; align-items: center; gap: 8px;">
                <span class="protocol-badge" style="background: #10b981; font-size: 11px; padding: 2px 8px;">SSE</span>
                ${s.serviceName ?? '未绑定'}
            </div>
        </td>
        <td><span class="badge ${s.active ? 'success' : ''}">${s.active ? '🟢 Active' : '🔴 Closed'}</span></td>
        <td>${formatTime(s.lastActive)}</td>
    </tr>
`).join('');
```

### 步骤 5: 修改 RESTful 请求列表显示

找到 `renderRestfulRequests` 函数中的以下代码（约第 832 行）：
```javascript
return `
    <tr data-request-id="${req.requestId}">
        <td>${req.serverName ?? '-'}</td>
        <td>${displayName}</td>
        <td>
            <span class="badge ${req.success ? 'success' : 'fail'}">
                ${req.responseStatus ?? '-'}
            </span>
        </td>
        <td>${formatTime(req.startTime)}</td>
    </tr>
`;
```

替换为：
```javascript
return `
    <tr data-request-id="${req.requestId}">
        <td>
            <div style="display: flex; align-items: center; gap: 8px;">
                <span class="protocol-badge" style="background: #2563eb; font-size: 11px; padding: 2px 8px;">RESTful</span>
                ${req.serverName ?? '-'}
            </div>
        </td>
        <td>
            <div style="display: flex; align-items: center; gap: 6px; flex-wrap: wrap;">
                ${req.mcpMethod ? `<span style="color: #6b7280; font-size: 11px; background: #f3f4f6; padding: 2px 6px; border-radius: 4px;">${req.mcpMethod}</span>` : ''}
                <span>${displayName}</span>
            </div>
        </td>
        <td>
            <span class="badge ${req.success ? 'success' : 'fail'}">
                ${req.responseStatus ?? '-'}
            </span>
        </td>
        <td>${formatTime(req.startTime)}</td>
    </tr>
`;
```

### 步骤 6: 更新标签页切换逻辑

找到标签页切换的 JavaScript 代码（约第 768 行）：
```javascript
if (targetTab === 'restful') {
    loadRestfulRequests();
} else if (targetTab === 'sse') {
    loadSseDashboard();
}
```

替换为：
```javascript
if (targetTab === 'restful') {
    loadRestfulRequests();
} else if (targetTab === 'sse') {
    loadSseDashboard();
} else if (targetTab === 'protocol') {
    // 协议概览页面不需要加载数据
}
```

### 步骤 7: 修改 SSE 标签页的 active 状态

找到 `<div class="tab-content active" id="sse-tab">`（约第 301 行），将 `active` 类移除：
```html
<div class="tab-content" id="sse-tab">
```

## ✅ 验证步骤

1. 打开浏览器访问 `http://localhost:8052/admin`
2. 检查"协议概览"标签页是否显示
3. 检查三种协议卡片是否正确显示
4. 检查 JSON-RPC 2.0 格式说明是否正确显示
5. 切换到"SSE 会话"标签页，检查是否显示 SSE 协议标识
6. 切换到"RESTful 请求"标签页，检查是否显示 RESTful 协议标识

## 🎨 预期效果

完成所有步骤后，UI 将具备以下特性：

1. ✅ **协议概览页面**：清晰展示三种协议的对比和特点
2. ✅ **JSON-RPC 2.0 格式说明**：语法高亮的格式展示
3. ✅ **协议标识**：SSE 和 RESTful 请求都显示协议徽章
4. ✅ **工作流程图**：展示每种协议的工作流程
5. ✅ **现代化设计**：统一的颜色系统和视觉风格

## 📝 注意事项

- 所有代码片段都需要精确匹配原文件中的内容
- 建议在修改前备份原文件
- 如果遇到问题，可以使用 git 恢复：`git restore src/main/resources/static/admin/index.html`

---

**创建日期**: 2025-01-12  
**版本**: v1.0

















