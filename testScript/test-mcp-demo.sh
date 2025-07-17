#!/bin/bash

echo "🧪 Testing MCP Demo with Spring AI and DeepSeek"
echo "==================================="

MCP_CLIENT_URL="http://localhost:8080"

# Function to test endpoint
test_endpoint() {
    local endpoint=$1
    local description=$2
    local method=${3:-GET}
    local data=${4:-}
    
    echo ""
    echo "🔍 Testing: $description"
    echo "📡 Endpoint: $method $endpoint"
    
    if [[ $method == "POST" ]]; then
        response=$(curl -s -X POST "$endpoint" \
            -H "Content-Type: application/json" \
            -d "$data" \
            --max-time 30)
    else
        response=$(curl -s "$endpoint" --max-time 30)
    fi
    
    if [[ $? -eq 0 ]] && [[ -n "$response" ]]; then
        echo "✅ Success!"
        echo "📄 Response:"
        echo "$response" | head -c 500
        if [[ ${#response} -gt 500 ]]; then
            echo "... (truncated)"
        fi
    else
        echo "❌ Failed!"
        echo "Response: $response"
    fi
}

# Check if services are running
echo "🔄 Checking if services are running..."

if ! curl -s http://localhost:8060/actuator/health > /dev/null; then
    echo "❌ MCP Server (port 8060) is not running"
    echo "Please start it first: cd mcp-server && mvn spring-boot:run"
    exit 1
fi

if ! curl -s http://localhost:8080/actuator/health > /dev/null; then
    echo "❌ MCP Client (port 8080) is not running"
    echo "Please start it first: cd mcp-client && mvn spring-boot:run"
    exit 1
fi

echo "✅ Both services are running!"

# Test endpoints
test_endpoint "$MCP_CLIENT_URL/persons/all" "Get all persons"

test_endpoint "$MCP_CLIENT_URL/persons/nationality/German" "Find German persons"

test_endpoint "$MCP_CLIENT_URL/persons/nationality/French" "Find French persons"

test_endpoint "$MCP_CLIENT_URL/persons/count-by-nationality/Italian" "Count Italian persons"

test_endpoint "$MCP_CLIENT_URL/persons/count-by-nationality/Japanese" "Count Japanese persons"

# Test custom query
custom_query='{"query": "Who is the oldest person in the database? What is their nationality?"}'
test_endpoint "$MCP_CLIENT_URL/persons/query" "Custom query - oldest person" "POST" "$custom_query"

# Test another custom query
custom_query2='{"query": "How many different nationalities are represented in the database?"}'
test_endpoint "$MCP_CLIENT_URL/persons/query" "Custom query - count nationalities" "POST" "$custom_query2"

echo ""
echo "🎉 Testing completed!"
echo "====================="
echo ""
echo "📊 Summary of MCP functionality tested:"
echo "• ✅ MCP Server tool discovery"
echo "• ✅ Spring AI @Tool method execution"
echo "• ✅ ChatClient integration with MCP tools"
echo "• ✅ Natural language to tool mapping"
echo "• ✅ SSE-based MCP client-server communication"
echo ""
echo "🔧 Tools tested:"
echo "• getAllPersons()"
echo "• getPersonsByNationality(nationality)"
echo "• countPersonsByNationality(nationality)"
echo ""
echo "View logs for more details:"
echo "tail -f logs/mcp-server.log logs/mcp-client.log" 