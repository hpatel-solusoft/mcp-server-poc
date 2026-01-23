"""
Claim Status Checker - Direct MCP Tool Client
Description: Connects to the Claims MCP Server and queries 'get_claim_status' directly.
"""
import asyncio
import json
import sys
import os
import traceback
import certifi
from pathlib import Path
from contextlib import AsyncExitStack
from dotenv import load_dotenv

# MCP Imports
from mcp import ClientSession
from mcp.client.sse import sse_client

# --- SECURITY CONFIGURATION (Reused from your existing agent) ---
local_cert_path = Path(__file__).parent.parent / "server_cert.pem"
combined_cert_path = Path(__file__).parent.parent / "combined_cert_bundle.pem"

if local_cert_path.exists():
    try:
        # Create hybrid bundle if needed
        with open(certifi.where(), "r", encoding="utf-8") as f:
            standard_certs = f.read()
        with open(local_cert_path, "r", encoding="utf-8") as f:
            local_cert = f.read()
        with open(combined_cert_path, "w", encoding="utf-8") as f:
            f.write(standard_certs + "\n" + local_cert)
        
        # Set Environment Variables
        os.environ["SSL_CERT_FILE"] = str(combined_cert_path.absolute())
        os.environ["REQUESTS_CA_BUNDLE"] = str(combined_cert_path.absolute())
    except Exception as e:
        print(f"[WARN] Failed to create hybrid cert bundle: {e}")
# -------------------------------------------------------------

load_dotenv()

class ClaimStatusClient:
    def __init__(self, config_path: str = "mcp_config.json"):
        self.config_path = Path(config_path)
        self.session: ClientSession | None = None
        self.exit_stack = AsyncExitStack()
        
    async def connect(self):
        """Establishes connection to the MCP Server"""
        # Load Config
        if not self.config_path.exists():
            # Fallback default if config is missing
            url = "https://localhost:9443/mcp/sse" 
            print(f"[INFO] Config not found, using default: {url}")
        else:
            with open(self.config_path, 'r') as f:
                config = json.load(f)
                # Assuming simple setup with one server for this specific client
                server_config = list(config["mcpServers"].values())[0]
                url = server_config["url"]

        print(f"[CONN] Connecting to Claims Server at {url}...")
        
        try:
            # Connect via SSE
            sse_transport = await self.exit_stack.enter_async_context(
                sse_client(url, headers={"X-MCP-API-KEY": os.getenv("MCP_SERVER_KEY", "")})
            )
            read, write = sse_transport
            self.session = await self.exit_stack.enter_async_context(
                ClientSession(read, write)
            )
            await self.session.initialize()
            print("[CONN] Connected successfully.")
            
        except Exception as e:
            print(f"[ERR] Connection failed: {e}")
            sys.exit(1)

    async def get_status(self, claim_id: str):
        """Calls the 'get_claim_status' tool directly"""
        if not self.session:
            print("[ERR] No active session.")
            return

        tool_name = "get_claim_status"
        tool_args = {"claimId": claim_id} # Matches Java parameter name directly

        print(f"\n[QUERY] Checking status for ID: {claim_id}...")
        
        try:
            # Direct MCP Tool Call
            result = await self.session.call_tool(tool_name, arguments=tool_args)
            
            # Extract text content
            result_text = ""
            if result.content:
                for content in result.content:
                    if hasattr(content, 'text'):
                        result_text += content.text
            
            # Parse the JSON returned by the Java tool
            try:
                data = json.loads(result_text)
                self._print_formatted_result(data)
            except json.JSONDecodeError:
                # Fallback if Java returns raw string
                print(f"[RAW RESULT] {result_text}")

        except Exception as e:
            print(f"[FAIL] Error calling tool: {e}")

    def _print_formatted_result(self, data: dict):
        """Pretty prints the JSON response"""
        print("-" * 40)
        print(f" CLAIM STATUS REPORT")
        print("-" * 40)
        
        if data.get("status") == "success":
            c_id = data.get("claim_id", "N/A")
            c_status = data.get("claim_status", "Unknown")
            
            print(f" ID      : {c_id}")
            print(f" STATUS  : {c_status.upper()}")
            
            # Handle cases where status might be 'unknown'
            if c_status.lower() == "unknown":
                print("\n[NOTE] The system could not find this claim ID in Case360.")
        else:
            print(f" ERROR   : {data.get('message', 'Unknown error')}")
            
        print("-" * 40)

    async def close(self):
        await self.exit_stack.aclose()

async def main():
    # Setup path to config file relative to this script
    project_root = Path(__file__).parent.parent
    config_file = project_root / "mcp_config.json"
    
    client = ClaimStatusClient(str(config_file))
    
    try:
        await client.connect()
        
        # Interactive Loop
        while True:
            user_input = input("\nEnter Claim ID (or 'q' to quit): ").strip()
            if user_input.lower() in ['q', 'quit', 'exit']:
                break
            
            if user_input:
                await client.get_status(user_input)
                
    finally:
        await client.close()
        print("[EXIT] Disconnected.")

if __name__ == "__main__":
    asyncio.run(main())