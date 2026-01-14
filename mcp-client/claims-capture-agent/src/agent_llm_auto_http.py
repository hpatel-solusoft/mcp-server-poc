"""
Claims Processing Agent - Multi-Server Configuration Version
"""
import asyncio
import json
import sys
import base64
from typing import Optional, Any, Dict
from pathlib import Path
from datetime import datetime
from contextlib import AsyncExitStack

# MCP Imports
from mcp import ClientSession
from mcp.client.sse import sse_client
from openai import OpenAI
from dotenv import load_dotenv

# Local Imports
sys.path.insert(0, str(Path(__file__).parent.parent))
from src.pdf_extractor import extract_text_from_pdf

load_dotenv()

class DocumentContext:
    """Manages documents for injection into tool calls (Unchanged)"""
    def __init__(self):
        self.documents = {}
    
    def register_document(self, doc_id: str, file_path: Path) -> dict:
        with open(file_path, 'rb') as file:
            pdf_bytes = file.read()
        self.documents[doc_id] = {
            'base64': base64.b64encode(pdf_bytes).decode('utf-8'),
            'filename': file_path.name,
            'size': len(pdf_bytes),
            'path': str(file_path)
        }
        return {'doc_id': doc_id, 'filename': file_path.name, 'size': len(pdf_bytes)}
    
    def get_document_base64(self, doc_id: str) -> Optional[str]:
        doc = self.documents.get(doc_id)
        return doc['base64'] if doc else None

class LLMClaimsAgent:
    """Agent that connects to multiple MCP servers via configuration"""
    
    def __init__(self, config_path: str = "mcp_config.json"):
        self.config_path = Path(config_path)
        self.sessions: Dict[str, ClientSession] = {} # Map server_name -> session
        self.exit_stack = AsyncExitStack()
        self.openai_client = OpenAI()
        
        # Tool Management
        self.available_tools = []
        self.tool_routing = {} # Map tool_name -> server_name
        
        self.doc_context = DocumentContext()
        self.uploaded_document_ids = {}
        
        # Injection Config (Same as before)
        self.DOCUMENT_INJECTION_CONFIG = {
            "upload_document": {"inject_params": {"document_base64": "current_document"}},
            "upload_motor_claim_document": {"inject_params": {"document_base64": "current_document"}},
            "create_motor_claim_workflow": {"inject_params": {"document_base64": "current_document"}}, 
            # Added create_motor_claim_workflow just in case the definition changes
        }
   
    def load_config(self) -> dict:
        if not self.config_path.exists():
            raise FileNotFoundError(f"Config file not found: {self.config_path}")
        with open(self.config_path, 'r') as f:
            return json.load(f)

    async def connect_to_servers(self):
        """Connect to all enabled MCP servers defined in config"""
        config = self.load_config()
        servers = config.get("mcpServers", {})
        
        print(f"\n🔌 Loading configuration from {self.config_path}")
        
        for server_name, server_config in servers.items():
            # Skip disabled servers
            if server_config.get("enabled", True) is False:
                continue

            url = server_config.get("url")
            transport_type = server_config.get("transport", "sse")

            if transport_type == "sse" and url:
                try:
                    print(f"   Connecting to '{server_name}' at {url}...")
                    
                    # Create Transport
                    sse_transport = await self.exit_stack.enter_async_context(
                        sse_client(url)
                    )
                    read, write = sse_transport
                    
                    # Create Session
                    session = await self.exit_stack.enter_async_context(
                        ClientSession(read, write)
                    )
                    
                    await session.initialize()
                    
                    # Store session
                    self.sessions[server_name] = session
                    print(f"   ✓ Connected to {server_name}")
                    
                except Exception as e:
                    print(f"   ❌ Failed to connect to {server_name}: {e}")
            else:
                print(f"   ⚠️ Unknown transport or missing URL for {server_name}")

        # After connecting to all, load tools
        await self.load_mcp_tools()
        
    async def disconnect(self):
        """Disconnect from all servers"""
        await self.exit_stack.aclose()
    
    async def load_mcp_tools(self):
        """Aggregate tools from ALL connected servers"""
        self.available_tools = []
        self.tool_routing = {}
        
        print(f"\n📋 Aggregating tools from {len(self.sessions)} servers...")
        
        for server_name, session in self.sessions.items():
            try:
                tools_response = await session.list_tools()
                
                for mcp_tool in tools_response.tools:
                    # Convert to OpenAI format
                    openai_tool = {
                        "type": "function",
                        "function": {
                            "name": mcp_tool.name,
                            "description": mcp_tool.description or f"MCP tool from {server_name}",
                            "parameters": mcp_tool.inputSchema if hasattr(mcp_tool, 'inputSchema') else {
                                "type": "object",
                                "properties": {},
                                "required": []
                            }
                        }
                    }
                    
                    self.available_tools.append(openai_tool)
                    
                    # VITAL: Map this tool to this specific server
                    self.tool_routing[mcp_tool.name] = server_name
                    print(f"  ✓ {mcp_tool.name} (via {server_name})")
                    
            except Exception as e:
                print(f"  ⚠️ Error listing tools for {server_name}: {e}")
        
        print(f"✓ Total tools loaded: {len(self.available_tools)}")
    
    async def call_mcp_tool(self, tool_name: str, tool_input: Dict[str, Any]) -> str:
        """Route the tool call to the correct server"""
        
        # 1. Find which server owns this tool
        server_name = self.tool_routing.get(tool_name)
        if not server_name:
            return json.dumps({"error": f"Tool '{tool_name}' not found in routing table"})
            
        session = self.sessions.get(server_name)
        if not session:
            return json.dumps({"error": f"Session for server '{server_name}' is not active"})

        print(f"  🔧 Calling MCP tool: {tool_name} on server: {server_name}")
        
        # Logging (truncated for brevity)
        log_args = {k: (f"{v[:50]}..." if isinstance(v, str) and len(v) > 100 else v) 
                   for k, v in tool_input.items()}
        print(f"     Arguments: {json.dumps(log_args, indent=2)}")
        
        # 2. Execute call on the specific session
        try:
            result = await session.call_tool(tool_name, arguments=tool_input)
            
            # Extract text content
            result_text = ""
            if result.content:
                for content in result.content:
                    if hasattr(content, 'text'):
                        result_text += content.text
            
            if not result_text:
                result_text = json.dumps({"status": "success", "content": "empty"})

            # Document ID Tracking (Same as original)
            if tool_name in self.DOCUMENT_INJECTION_CONFIG:
                try:
                    res_json = json.loads(result_text)
                    if "document_id" in res_json:
                        doc_ref = tool_input.get("document_name", "unknown")
                        self.uploaded_document_ids[doc_ref] = str(res_json["document_id"])
                except:
                    pass
            
            return result_text

        except Exception as e:
            return json.dumps({"error": f"Execution failed on {server_name}: {str(e)}"})
    
    # ... [inject_document_data, process_claim_with_llm, generate_summary remain the same] ...
    # Be sure to include the inject_document_data, process_claim_with_llm, 
    # and generate_summary methods from your original code here.
    # They do not need modification because they just call self.call_mcp_tool()
    
    def inject_document_data(self, tool_name: str, tool_args: dict, current_doc_id: str) -> dict:
        """(Copy from original file)"""
        # ... copy existing implementation ...
        if tool_name not in self.DOCUMENT_INJECTION_CONFIG:
            return tool_args
        
        config = self.DOCUMENT_INJECTION_CONFIG[tool_name]
        inject_map = config.get("inject_params", {})
        
        for param_name, context_key in inject_map.items():
            if param_name in tool_args:
                param_value = tool_args[param_name]
                is_doc_reference = (not param_value or param_value == "DOCUMENT_DATA" or param_value == current_doc_id)
                
                if is_doc_reference:
                    actual_base64 = self.doc_context.get_document_base64(current_doc_id)
                    if actual_base64:
                        tool_args[param_name] = actual_base64
        return tool_args


    async def process_claim_with_llm(self, document_path: Path, output_dir: Path):
        """Process a claim using OpenAI to orchestrate MCP tools (ORIGINAL PROMPT RESTORED)"""
        print(f"\n🔄 Processing claim: {document_path.name}")
        print("=" * 70)
        
        print("\n[Step 1] Extracting text from PDF...")
        # 1. Extract text locally first
        document_text = extract_text_from_pdf(document_path)
        print(f"✓ Extracted {len(document_text)} characters")
        
        # 2. Register document in local context
        doc_id = f"doc_{datetime.now().strftime('%Y%m%d%H%M%S')}"
        doc_info = self.doc_context.register_document(doc_id, document_path)
        print(f"✓ Registered document: {doc_id} ({doc_info['filename']}, {doc_info['size']} bytes)")
        
        # --- ORIGINAL PROMPT START ---
        system_prompt = """You are an intelligent claims processing agent with access to MCP tools for processing insurance claims.

Your task is to:
1. Analyze the claim document text provided
2. Use the appropriate tools to understand the claim type and extract relevant information
3. Upload the claim document as needed
4. Based on the claim type (motor or healthcare), create the appropriate workflow
    IMPORTANT: Pass the document_id from the upload response to the workflow creation tool
5. Store the claim record
6. Provide a summary of what you did

Be thorough and use the tools intelligently. If you encounter errors, explain what happened.
Always complete all steps of the process."""

        messages = [
            {
                "role": "system",
                "content": system_prompt
            },
            {
                "role": "user",
                "content": f"""Please process this insurance claim document. Here is the extracted text from the PDF:

--- CLAIM DOCUMENT START ---
{document_text[:4000]}  
{"... (document truncated)" if len(document_text) > 4000 else ""}
--- CLAIM DOCUMENT END ---

Please analyze this claim and use the available MCP tools to:
1. Extract the claim information and determine its type
2. Upload the claim document
3. Create the appropriate workflow in Case360
4. Store the claim record in the database
5. Provide me with a summary of the processing results"""
            }
        ]
        # --- ORIGINAL PROMPT END ---
        
        print("\n[Step 2] Starting LLM-driven claim processing...")
        print("🤖 GPT is analyzing the claim and deciding which tools to use...\n")
        
        max_iterations = 10
        iteration = 0
        
        while iteration < max_iterations:
            iteration += 1
            print(f"\n--- Iteration {iteration} ---")
            
            # Call OpenAI
            response = self.openai_client.chat.completions.create(
                model="gpt-4o",
                messages=messages,
                tools=self.available_tools,
                tool_choice="auto"
            )
            
            message = response.choices[0].message
            finish_reason = response.choices[0].finish_reason
            
            print(f"GPT's response (finish_reason: {finish_reason}):")
            
            messages.append({
                "role": "assistant",
                "content": message.content,
                "tool_calls": message.tool_calls if message.tool_calls else None
            })
            
            if message.content:
                print(f"  💬 {message.content[:200]}...")
            
            # Handle Tool Calls
            if finish_reason == "tool_calls" and message.tool_calls:
                for tool_call in message.tool_calls:
                    function_name = tool_call.function.name
                    function_args = json.loads(tool_call.function.arguments)
                    
                    print(f"  🔧 Tool: {function_name}")
                    
                    # Inject Document Data (Base64) if needed
                    function_args = self.inject_document_data(
                        function_name, 
                        function_args, 
                        doc_id
                    )
                    
                    # CALL THE TOOL (Using the new routed method)
                    tool_result = await self.call_mcp_tool(function_name, function_args)
                    print(f"  ✓ Tool result: {tool_result[:150]}...")
                    
                    # Post-processing to help LLM see the document_id (Same logic as original)
                    enhanced_result = tool_result
                    if function_name in self.DOCUMENT_INJECTION_CONFIG:
                        try:
                            result_json = json.loads(tool_result)
                            if "document_id" in result_json:
                                doc_id_value = result_json["document_id"]
                                result_json["_note_for_llm"] = f"Document uploaded successfully. Use document_id={doc_id_value} in subsequent workflow creation calls."
                                enhanced_result = json.dumps(result_json, indent=2)
                                print(f"     💡 Enhanced result with document_id reference for LLM")
                        except json.JSONDecodeError:
                            pass
                    
                    messages.append({
                        "role": "tool",
                        "tool_call_id": tool_call.id,
                        "name": function_name,
                        "content": enhanced_result
                    })
            
            elif finish_reason == "stop":
                print("\n✓ GPT has completed the claim processing")
                break
            else:
                print(f"\n⚠️  Unexpected finish reason: {finish_reason}")
                break
        
        # Final Output Generation (Unchanged)
        if iteration >= max_iterations:
            print(f"\n⚠️  Reached maximum iterations ({max_iterations})")
            
        final_response = message.content or "No final response from GPT"
        claim_id = f"CLM-{datetime.now().strftime('%Y%m%d%H%M%S')}"
        
        output_data = {
            "claim_id": claim_id,
            "document_name": document_path.name,
            "document_id": doc_id,
            "uploaded_document_ids": self.uploaded_document_ids,
            "processing_date": datetime.now().isoformat(),
            "llm_model": "gpt-4o",
            "iterations": iteration,
            "final_response": final_response,
            "conversation_history": messages
        }
        
        # Save to file logic...
        json_path = output_dir / f"{claim_id}.json"
        with open(json_path, 'w') as f:
            json.dump(output_data, f, indent=2, default=str)
        print(f"\n💾 Saved processing results: {json_path}")
            
        summary = self.generate_summary(output_data, final_response)
        summary_path = output_dir / f"{claim_id}_summary.txt"
        with open(summary_path, 'w') as f:
            f.write(summary)
        print(f"💾 Saved summary: {summary_path}")
            
        print("\n" + "=" * 70)
        print("✅ Claim processing completed!")
        print("=" * 70)
        
        return output_data


    def generate_summary(self, data: dict, llm_response: str) -> str:
            """Generate human-readable summary"""
            return f"""CLAIM PROCESSING SUMMARY (LLM-Driven via HTTP)
    {'=' * 70}
    Claim ID: {data['claim_id']}
    Document: {data['document_name']}
    Document ID: {data.get('document_id', 'N/A')}
    Processed: {datetime.now().strftime('%B %d, %Y at %I:%M %p')}
    Model: {data['llm_model']}
    Iterations: {data['iterations']}

    GPT's ANALYSIS AND ACTIONS
    {'-' * 70}
    {llm_response}

    {'=' * 70}
    End of Report
    """
# --- MAIN ---
async def main():
    project_root = Path(__file__).parent.parent
    config_file = project_root / "mcp_config.json"
    
    # Create dummy config if it doesn't exist for testing
    if not config_file.exists():
        with open(config_file, 'w') as f:
            json.dump({
                "mcpServers": {
                    "claims-server": {
                        "url": "http://localhost:8000/sse",
                        "transport": "sse"
                    }
                }
            }, f, indent=2)
            print(f"Created default config at {config_file}")

    agent = LLMClaimsAgent(str(config_file))
    
    try:
        await agent.connect_to_servers()
        
        # Example: Process files in input directory
        input_dir = project_root / "input"
        output_dir = project_root / "output"
        # ... (rest of main logic) ...
        
    finally:
        await agent.disconnect()

if __name__ == "__main__":
    asyncio.run(main())