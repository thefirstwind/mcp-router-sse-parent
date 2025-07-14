#!/bin/bash

# Script to start Nacos server locally for testing

NACOS_VERSION="2.3.0"
NACOS_DIR="nacos"
NACOS_DOWNLOAD_URL="https://github.com/alibaba/nacos/releases/download/${NACOS_VERSION}/nacos-server-${NACOS_VERSION}.tar.gz"

echo "Starting Nacos server for MCP Router testing..."

# # Check if Nacos is already running
# if curl -s http://127.0.0.1:8848/nacos/v1/console/health | grep -q "UP"; then
#     echo "Nacos is already running on port 8848"
#     exit 0
# fi

# # Check if Nacos directory exists
# if [ ! -d "$NACOS_DIR" ]; then
#     echo "Nacos not found. Downloading Nacos ${NACOS_VERSION}..."
    
#     # Download Nacos
#     curl -L "$NACOS_DOWNLOAD_URL" -o nacos-server.tar.gz
    
#     if [ $? -ne 0 ]; then
#         echo "Failed to download Nacos. Please download manually from:"
#         echo "$NACOS_DOWNLOAD_URL"
#         exit 1
#     fi
    
#     # Extract Nacos
#     tar -xzf nacos-server.tar.gz
#     rm nacos-server.tar.gz
    
#     if [ ! -d "$NACOS_DIR" ]; then
#         echo "Failed to extract Nacos"
#         exit 1
#     fi
# fi

# # Start Nacos in standalone mode
# echo "Starting Nacos in standalone mode..."
# cd "$NACOS_DIR"

# # Check if we're on macOS or Linux
# if [[ "$OSTYPE" == "darwin"* ]]; then
#     # macOS
#     sh bin/startup.sh -m standalone
# else
#     # Linux
#     sh bin/startup.sh -m standalone
# fi

# if [ $? -eq 0 ]; then
#     echo "Nacos startup script executed successfully"
#     echo "Waiting for Nacos to be ready..."
    
#     # Wait for Nacos to be ready (maximum 60 seconds)
#     for i in {1..60}; do
#         if curl -s http://127.0.0.1:8848/nacos/v1/console/health | grep -q "UP"; then
#             echo "Nacos is now running and ready!"
#             echo "Nacos Console: http://127.0.0.1:8848/nacos"
#             echo "Default credentials: nacos/nacos"
#             exit 0
#         fi
#         echo "Waiting for Nacos to start... ($i/60)"
#         sleep 1
#     done
    
#     echo "Nacos did not start within 60 seconds. Please check the logs:"
#     echo "tail -f $NACOS_DIR/logs/start.out"
#     exit 1
# else
#     echo "Failed to start Nacos"
#     exit 1
# fi 

# # Script to start Nacos server locally for testing

# NACOS_VERSION="2.3.0"
# NACOS_DIR="nacos"
# NACOS_DOWNLOAD_URL="https://github.com/alibaba/nacos/releases/download/${NACOS_VERSION}/nacos-server-${NACOS_VERSION}.tar.gz"

# echo "Starting Nacos server for MCP Router testing..."

# # Check if Nacos is already running
# if curl -s http://127.0.0.1:8848/nacos/v1/console/health | grep -q "UP"; then
#     echo "Nacos is already running on port 8848"
#     exit 0
# fi

# # Check if Nacos directory exists
# if [ ! -d "$NACOS_DIR" ]; then
#     echo "Nacos not found. Downloading Nacos ${NACOS_VERSION}..."
    
#     # Download Nacos
#     curl -L "$NACOS_DOWNLOAD_URL" -o nacos-server.tar.gz
    
#     if [ $? -ne 0 ]; then
#         echo "Failed to download Nacos. Please download manually from:"
#         echo "$NACOS_DOWNLOAD_URL"
#         exit 1
#     fi
    
#     # Extract Nacos
#     tar -xzf nacos-server.tar.gz
#     rm nacos-server.tar.gz
    
#     if [ ! -d "$NACOS_DIR" ]; then
#         echo "Failed to extract Nacos"
#         exit 1
#     fi
# fi

# # Start Nacos in standalone mode
# echo "Starting Nacos in standalone mode..."
# cd "$NACOS_DIR"

# # Check if we're on macOS or Linux
# if [[ "$OSTYPE" == "darwin"* ]]; then
#     # macOS
#     sh bin/startup.sh -m standalone
# else
#     # Linux
#     sh bin/startup.sh -m standalone
# fi

# if [ $? -eq 0 ]; then
#     echo "Nacos startup script executed successfully"
#     echo "Waiting for Nacos to be ready..."
    
#     # Wait for Nacos to be ready (maximum 60 seconds)
#     for i in {1..60}; do
#         if curl -s http://127.0.0.1:8848/nacos/v1/console/health | grep -q "UP"; then
#             echo "Nacos is now running and ready!"
#             echo "Nacos Console: http://127.0.0.1:8848/nacos"
#             echo "Default credentials: nacos/nacos"
#             exit 0
#         fi
#         echo "Waiting for Nacos to start... ($i/60)"
#         sleep 1
#     done
    
#     echo "Nacos did not start within 60 seconds. Please check the logs:"
#     echo "tail -f $NACOS_DIR/logs/start.out"
#     exit 1
# else
#     echo "Failed to start Nacos"
#     exit 1
# fi 