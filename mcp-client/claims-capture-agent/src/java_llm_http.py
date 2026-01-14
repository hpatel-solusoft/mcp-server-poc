import asyncio
import json
import sys
import base64
import traceback
from typing import Optional, Any, Dict
from pathlib import Path
from datetime import datetime
from contextlib import AsyncExitStack
import anyio

# MCP Imports
from mcp import ClientSession
from mcp.client.sse import sse_client
from openai import OpenAI
from dotenv import load_dotenv

# Local Imports
sys.path.insert(0, str(Path(__file__).parent.parent))
from src.pdf_extractor import extract_text_from_pdf

load_dotenv()

class LLMClaimsAgent:
    """MCP Client for Claims Processing with LLM Integration"""
    
    def __init__(self, config_path: str):
        """Initialize the LLM Claims Agent with MCP configuration"""
        self.config_path = config_path
        self.sessions: Dict[str, ClientSession] = {}
        self.tools: Dict[str, Any] = {}
        
    async def connect_to_servers(self):
        """Connect to all configured MCP servers"""
        try:
            with open(self.config_path, 'r') as f:
                config = json.load(f)
            
            mcp_servers = config.get("mcpServers", {})
            send_stream, receive_stream = anyio.create_memory_object_stream()
            for server_name, server_config in mcp_servers.items():
                try:
                    url = server_config.get("url")
                    if url:
                        print(f"Connecting to MCP server: {server_name} at {url}")
                        async with sse_client(url) as client:
                            async with ClientSession(client, send_stream) as session:
                                self.sessions[server_name] = session
                                await self._load_tools(server_name, session)
                                print(f"Successfully connected to {server_name}")
                except Exception as e:
                    print(f"Error connecting to {server_name}: {e}")
                    traceback.print_exc()
        except Exception as e:
            print(f"Error reading MCP config: {e}")
            traceback.print_exc()
    
    async def _load_tools(self, server_name: str, session: ClientSession):
        """Load available tools from connected MCP server"""
        try:
            response = await session.list_tools()
            self.tools[server_name] = response.tools
            print(f"Loaded {len(response.tools)} tools from {server_name}")
            for tool in response.tools:
                print(f"  - {tool.name}")
        except Exception as e:
            print(f"Error loading tools from {server_name}: {e}")
            traceback.print_exc()
    
    async def call_tool_test(self, **kwargs) -> Dict[str, Any]:
        """Call the 'tool_test' MCP tool"""
        try:
            for server_name, session in self.sessions.items():
                try:
                    # Call the tool_test tool
                    response = await session.call_tool("tool_test", kwargs)
                    print(f"Tool 'tool_test' called successfully on {server_name}")
                    return {
                        "success": True,
                        "server": server_name,
                        "result": response.content
                    }
                except Exception as e:
                    print(f"Error calling tool_test on {server_name}: {e}")
                    traceback.print_exc()
                    continue
            
            return {
                "success": False,
                "error": "Failed to call tool_test on any server"
            }
        except Exception as e:
            print(f"Error in call_tool_test: {e}")
            traceback.print_exc()
            return {
                "success": False,
                "error": str(e)
            }
    
    async def disconnect(self):
        """Disconnect from all MCP servers"""
        for server_name, session in self.sessions.items():
            try:
                await session.close()
                print(f"Disconnected from {server_name}")
            except Exception as e:
                print(f"Error disconnecting from {server_name}: {e}")
                traceback.print_exc()
        self.sessions.clear()

# --- MAIN ---
async def main():
    project_root = Path(__file__).parent.parent
    config_file = project_root / "mcp_config.json"
    
    # Create dummy config if it doesn't exist for testing
    if not config_file.exists():
        with open(config_file, 'w') as f:
            json.dump({
                "mcpServers": {
                    "claims-server-java": {
                        "url": "http://localhost:9090/mcp/sse",
                        "transport": "sse"
                    }
                }
            }, f, indent=2)
            print(f"Created default config at {config_file}")

    agent = LLMClaimsAgent(str(config_file))
    
    try:
        await agent.connect_to_servers()
        
        # Example: Call the tool_test MCP tool
        print("\n--- Calling tool_test ---")
        result = await agent.call_tool_test()
        print(f"Tool result: {result}")
        
        # Example: Process files in input directory
        input_dir = project_root / "input"
        output_dir = project_root / "output"
        # ... (rest of main logic) ...
        
    finally:
        await agent.disconnect()

if __name__ == "__main__":
    asyncio.run(main())