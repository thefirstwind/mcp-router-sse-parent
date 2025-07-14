#!/bin/bash

# Demo script for registering MCP servers

echo "Registering demo MCP servers..."

BASE_URL="http://localhost:8000/api/mcp/register"

# Register File System MCP Server
echo "Registering File System MCP Server..."
curl -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "serverName": "mcp-filesystem",
    "ip": "localhost",
    "port": 3001,
    "transportType": "stdio",
    "description": "File system operations MCP server",
    "version": "1.0.0",
    "installCommand": "npx @modelcontextprotocol/server-filesystem",
    "metadata": {
      "category": "filesystem",
      "capabilities": "read,write,list"
    }
  }'

echo -e "\n"

# Register Database MCP Server
echo "Registering Database MCP Server..."
curl -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "serverName": "mcp-database",
    "ip": "localhost", 
    "port": 3002,
    "transportType": "stdio",
    "description": "Database operations MCP server",
    "version": "1.0.0",
    "installCommand": "npx @modelcontextprotocol/server-sqlite",
    "metadata": {
      "category": "database",
      "capabilities": "query,insert,update,delete"
    }
  }'

echo -e "\n"

# Register Web Search MCP Server
echo "Registering Web Search MCP Server..."
curl -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "serverName": "mcp-web-search",
    "ip": "localhost",
    "port": 3003,
    "transportType": "sse",
    "description": "Web search MCP server using Brave Search API",
    "version": "1.0.0",
    "metadata": {
      "category": "search",
      "capabilities": "web_search,news_search"
    }
  }'

echo -e "\n"

# Register Git MCP Server
echo "Registering Git MCP Server..."
curl -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "serverName": "mcp-git",
    "ip": "localhost",
    "port": 3004,
    "transportType": "stdio",
    "description": "Git operations MCP server",
    "version": "1.0.0",
    "installCommand": "npx @modelcontextprotocol/server-git",
    "metadata": {
      "category": "version_control",
      "capabilities": "clone,commit,push,pull,status"
    }
  }'

echo -e "\n"

echo "Demo servers registered successfully!"
echo "Use 'curl http://localhost:8000/api/mcp/servers' to list all registered servers." 

# Demo script for registering MCP servers

echo "Registering demo MCP servers..."

BASE_URL="http://localhost:8000/api/mcp/register"

# Register File System MCP Server
echo "Registering File System MCP Server..."
curl -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "serverName": "mcp-filesystem",
    "ip": "localhost",
    "port": 3001,
    "transportType": "stdio",
    "description": "File system operations MCP server",
    "version": "1.0.0",
    "installCommand": "npx @modelcontextprotocol/server-filesystem",
    "metadata": {
      "category": "filesystem",
      "capabilities": "read,write,list"
    }
  }'

echo -e "\n"

# Register Database MCP Server
echo "Registering Database MCP Server..."
curl -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "serverName": "mcp-database",
    "ip": "localhost", 
    "port": 3002,
    "transportType": "stdio",
    "description": "Database operations MCP server",
    "version": "1.0.0",
    "installCommand": "npx @modelcontextprotocol/server-sqlite",
    "metadata": {
      "category": "database",
      "capabilities": "query,insert,update,delete"
    }
  }'

echo -e "\n"

# Register Web Search MCP Server
echo "Registering Web Search MCP Server..."
curl -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "serverName": "mcp-web-search",
    "ip": "localhost",
    "port": 3003,
    "transportType": "sse",
    "description": "Web search MCP server using Brave Search API",
    "version": "1.0.0",
    "metadata": {
      "category": "search",
      "capabilities": "web_search,news_search"
    }
  }'

echo -e "\n"

# Register Git MCP Server
echo "Registering Git MCP Server..."
curl -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "serverName": "mcp-git",
    "ip": "localhost",
    "port": 3004,
    "transportType": "stdio",
    "description": "Git operations MCP server",
    "version": "1.0.0",
    "installCommand": "npx @modelcontextprotocol/server-git",
    "metadata": {
      "category": "version_control",
      "capabilities": "clone,commit,push,pull,status"
    }
  }'

echo -e "\n"

echo "Demo servers registered successfully!"
echo "Use 'curl http://localhost:8000/api/mcp/servers' to list all registered servers." 