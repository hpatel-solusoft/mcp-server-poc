"""
Claims Processing Agent - Multi-Server Configuration Version
SAFE MODE: No Emojis (ASCII Only)
FIXED: Document Name Injection & Logging
"""
import asyncio
import json
import sys
import base64
import os
import traceback
from typing import Optional, Any, Dict
from pathlib import Path
from datetime import datetime
from contextlib import AsyncExitStack
import httpx 
import certifi
from pathlib import Path

# --- SECURITY CONFIGURATION ---
# Point to the certificate file relative to this script
# (Assuming script is in src/ and cert is in project root)
local_cert_path = Path(__file__).parent.parent / "server_cert.pem"
combined_cert_path = Path(__file__).parent.parent / "combined_cert_bundle.pem"

if local_cert_path.exists():
    try:
        # 1. Read the Standard Internet Certs (for OpenAI)
        with open(certifi.where(), "r", encoding="utf-8") as f:
            standard_certs = f.read()

        # 2. Read your Local Java Server Cert
        with open(local_cert_path, "r", encoding="utf-8") as f:
            local_cert = f.read()

        # 3. Write them into a single new file
        with open(combined_cert_path, "w", encoding="utf-8") as f:
            f.write(standard_certs + "\n" + local_cert)

        print(f"[SEC] Created hybrid trust bundle at: {combined_cert_path.name}")

        # 4. Tell Python to use this Hybrid Bundle
        os.environ["SSL_CERT_FILE"] = str(combined_cert_path.absolute())
        os.environ["REQUESTS_CA_BUNDLE"] = str(combined_cert_path.absolute())

    except Exception as e:
        print(f"[WARN] Failed to create hybrid cert bundle: {e}")
else:
    print(f"[WARN] Local certificate not found at {local_cert_path}. SSL errors may occur.")
# ------------------------------
# 1. Force UTF-8 and replacement of bad characters
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
sys.stderr.reconfigure(encoding='utf-8', errors='replace')

# MCP Imports
from mcp import ClientSession
from mcp.client.sse import sse_client
from openai import OpenAI
from dotenv import load_dotenv

# Local Imports
sys.path.insert(0, str(Path(__file__).parent.parent))
try:
    from src.pdf_extractor import extract_text_from_pdf
except ImportError:
    print("[WARN] Could not import extract_text_from_pdf. Using dummy extractor.")
    def extract_text_from_pdf(path): return "Dummy extracted text for testing."

load_dotenv()

class DocumentContext:
    """Manages documents for injection into tool calls"""
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

    # --- NEW METHOD: Get Filename ---
    def get_document_filename(self, doc_id: str) -> Optional[str]:
        doc = self.documents.get(doc_id)
        return doc['filename'] if doc else None

class LLMClaimsAgent:
    """Agent that connects to multiple MCP servers via configuration"""
    
    def __init__(self, config_path: str = "mcp_config.json"):
        self.config_path = Path(config_path)
        self.sessions: Dict[str, ClientSession] = {} 
        self.exit_stack = AsyncExitStack()
        self.openai_client = OpenAI()
        
        self.available_tools = []
        self.tool_routing = {} 
        self.doc_context = DocumentContext()
        self.uploaded_document_ids = {}
        
        # Injection Config (Matches Java Parameter Names)
        self.DOCUMENT_INJECTION_CONFIG = {
            "upload_document": {
                "inject_params": {
                    "documentBase64": "current_document", 
                    "documentName": "current_document_name" # <--- This key triggers the filename injection
                }
            },
            # Also mapping the snake_case tool name just in case
            "uploadDocument": {
                "inject_params": {
                    "documentBase64": "current_document", 
                    "documentName": "current_document_name"
                }
            },

        }
   
    def load_config(self) -> dict:
        if not self.config_path.exists():
            raise FileNotFoundError(f"Config file not found: {self.config_path}")
        with open(self.config_path, 'r') as f:
            return json.load(f)

    async def connect_to_servers(self):
        config = self.load_config()
        servers = config.get("mcpServers", {})
        
        print(f"\n[INIT] Loading configuration from {self.config_path}")
        
        # 1. Retrieve the Key
        api_key = os.getenv("MCP_SERVER_KEY")
        headers = {}
        if api_key:
            headers["X-MCP-API-KEY"] = api_key
            print("   [SEC] Loaded MCP API Key from environment")
        else:
            print("   [WARN] No MCP_SERVER_KEY found in .env")

        for server_name, server_config in servers.items():
            if server_config.get("enabled", True) is False:
                continue

            url = server_config.get("url")
            transport_type = server_config.get("transport", "sse")

            if transport_type == "sse" and url:
                try:
                    print(f"   Connecting to '{server_name}' at {url}...")
                    
                    # REVERTED: We removed the 'client=' argument.
                    # The monkeypatch at the top of the file now handles the SSL bypass.
                    sse_transport = await self.exit_stack.enter_async_context(
                        sse_client(url, headers=headers) 
                    )
                    
                    read, write = sse_transport
                    
                    session = await self.exit_stack.enter_async_context(
                        ClientSession(read, write)
                    )
                    
                    await session.initialize()
                    self.sessions[server_name] = session
                    print(f"   [OK] Connected to {server_name}")
                    
                except Exception as e:
                    traceback.print_exc()
                    print(f"   [ERR] Failed to connect to {server_name}: {e}")
                    # Optional: print detailed error for debugging
                    # import traceback; traceback.print_exc()

        await self.load_mcp_tools()
        
    async def disconnect(self):
        await self.exit_stack.aclose()
    
    async def load_mcp_tools(self):
        self.available_tools = []
        self.tool_routing = {}
        
        print(f"\n[LIST] Aggregating tools from {len(self.sessions)} servers...")
        
        for server_name, session in self.sessions.items():
            try:
                tools_response = await session.list_tools()
                
                for mcp_tool in tools_response.tools:
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
                    self.tool_routing[mcp_tool.name] = server_name
                    print(f"  [+] {mcp_tool.name} (via {server_name})")
                    
            except Exception as e:
                print(f"  [WARN] Error listing tools for {server_name}: {e}")
        
        print(f"[OK] Total tools loaded: {len(self.available_tools)}")
    
    async def call_mcp_tool(self, tool_name: str, tool_input: Dict[str, Any]) -> str:
        server_name = self.tool_routing.get(tool_name)
        if not server_name:
            return json.dumps({"error": f"Tool '{tool_name}' not found in routing table"})
            
        session = self.sessions.get(server_name)
        if not session:
            return json.dumps({"error": f"Session for server '{server_name}' is not active"})

        print(f"  [TOOL] Calling: {tool_name} on server: {server_name}")
        
        log_args = {k: (f"{v[:50]}..." if isinstance(v, str) and len(v) > 100 else v) 
                   for k, v in tool_input.items()}
        print(f"    Arguments: {json.dumps(log_args, indent=2)}")
        
        try:
            result = await session.call_tool(tool_name, arguments=tool_input)
            
            result_text = ""
            if result.content:
                for content in result.content:
                    if hasattr(content, 'text'):
                        result_text += content.text
            
            if not result_text:
                result_text = json.dumps({"status": "success", "content": "empty"})

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

    # --- FIXED INJECTION METHOD ---
    def inject_document_data(self, tool_name: str, tool_args: dict, current_doc_id: str) -> dict:
        """
        Injects large payloads (Base64) and filenames into tool arguments.
        """
        if tool_name not in self.DOCUMENT_INJECTION_CONFIG:
            return tool_args
        
        config = self.DOCUMENT_INJECTION_CONFIG[tool_name]
        inject_map = config.get("inject_params", {})
        
        for param_name, context_key in inject_map.items():
            # 1. Inject Base64 Content
            if context_key == "current_document":
                actual_base64 = self.doc_context.get_document_base64(current_doc_id)
                if actual_base64:
                    tool_args[param_name] = actual_base64
                    print(f"    [INJ] Injected {len(actual_base64)} chars of Base64 into '{param_name}'")
                else:
                    print(f"    [WARN] No document content found for ID {current_doc_id}")

            # 2. Inject Filename (FIXED: Added this logic)
            elif context_key == "current_document_name":
                actual_filename = self.doc_context.get_document_filename(current_doc_id)
                if actual_filename:
                    tool_args[param_name] = actual_filename
                    print(f"    [INJ] Injected filename '{actual_filename}' into '{param_name}'")
                else:
                    print(f"    [WARN] No filename found for ID {current_doc_id}")

        return tool_args

    async def process_claim_with_llm(self, document_path: Path, output_dir: Path):
        print(f"\n[PROCESS] Processing claim: {document_path.name}")
        print("=" * 70)
        
        print("\n[Step 1] Extracting text from PDF...")
        document_text = extract_text_from_pdf(document_path)
        print(f"[OK] Extracted {len(document_text)} characters")
        
        doc_id = f"doc_{datetime.now().strftime('%Y%m%d%H%M%S')}"
        doc_info = self.doc_context.register_document(doc_id, document_path)
        print(f"[OK] Registered document: {doc_id} ({doc_info['filename']}, {doc_info['size']} bytes)")
        
        system_prompt = """You are an intelligent claims processing agent with access to MCP tools for processing insurance claims.

CRITICAL INSTRUCTION: VALIDATE BEFORE EXECUTION
Before calling ANY tools, analyze the document text to determine if it is a valid claim document.

IF the document is:
- A random file (recipe, manual, invoice for non-insurance items)
- Too vague to extract a policy number or claimant name or any claim info

THEN:
1. TERMINATE the process immediately.
2. Respond with: "REJECTED: Document does not appear to be a valid insurance claim."

ONLY if the document is a valid claim, proceed with these steps:
1. Analyze the claim document text provided
2. Use the appropriate tools to understand the claim type and extract relevant information
3. Upload the claim document as needed
4. Based on the claim type (motor or healthcare), create the appropriate workflow
   IMPORTANT: Pass the document_id from the upload response to the workflow creation tool
5. Store the claim record
    IMPORTANT: Pass the claim_id from the the workflow creation tool response to the claim storage tool
6. Provide a summary of what you did

Be thorough and use the tools intelligently. If you encounter errors, explain what happened."""

        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"""Please process this insurance claim document. Here is the extracted text from the PDF:

--- CLAIM DOCUMENT START ---
{document_text[:4000]}  
{"... (document truncated)" if len(document_text) > 4000 else ""}
--- CLAIM DOCUMENT END ---

Please analyze this claim and use the available MCP tools to:
1. Extract the claim information and determine its type
2. Upload the claim document
3. Create the appropriate workflow in Case360
4. Store the claim record in the database
5. Provide me with a summary of the processing results"""}
        ]
        
        print("\n[Step 2] Starting LLM-driven claim processing...")
        print("[GPT] Analyzing the claim and deciding which tools to use...\n")
        
        max_iterations = 10
        iteration = 0
        processing_failed = False
        failure_reason = ""
        
        while iteration < max_iterations:
            iteration += 1
            print(f"\n--- Iteration {iteration} ---")
            
            response = self.openai_client.chat.completions.create(
                model="gpt-4o",
                messages=messages,
                tools=self.available_tools,
                tool_choice="auto"
            )
            
            message = response.choices[0].message
            finish_reason = response.choices[0].finish_reason
            
            messages.append(message)
            
            if message.content:
                print(f"  [GPT] {message.content[:200]}...")
            
            if finish_reason == "tool_calls" and message.tool_calls:
                for tool_call in message.tool_calls:
                    function_name = tool_call.function.name
                    function_args = json.loads(tool_call.function.arguments)
                    
                    print(f"  [TOOL] {function_name}")
                    
                    # Inject Data
                    function_args = self.inject_document_data(function_name, function_args, doc_id)
                    
                    # Call Tool
                    tool_result = await self.call_mcp_tool(function_name, function_args)
                    print(f"  [RES] {tool_result[:150]}...")

                    # --- ERROR CHECKING ---
                    is_error = False
                    try:
                        res_json = json.loads(tool_result)
                        if res_json.get("success") is False or "error" in res_json:
                            is_error = True
                            failure_reason = res_json.get("error", "Unknown Tool Error")
                            if not failure_reason and "message" in res_json:
                                failure_reason = res_json["message"]
                    except:
                        pass

                    if is_error:
                        print(f"  [STOP] Tool '{function_name}' failed.")
                        print(f"  [FAIL] Reason: {failure_reason}")
                        processing_failed = True
                        
                        messages.append({
                            "role": "tool",
                            "tool_call_id": tool_call.id,
                            "name": function_name,
                            "content": tool_result
                        })
                        break # Exit tool loop
                    # ----------------------

                    enhanced_result = tool_result
                    if function_name in self.DOCUMENT_INJECTION_CONFIG:
                        try:
                            result_json = json.loads(tool_result)
                            if "document_id" in result_json:
                                doc_id_value = result_json["document_id"]
                                result_json["_note_for_llm"] = f"Document uploaded successfully. Use document_id={doc_id_value} in subsequent workflow creation calls."
                                enhanced_result = json.dumps(result_json, indent=2)
                                print(f"    [INFO] Enhanced result with document_id reference for LLM")
                        except json.JSONDecodeError:
                            pass
                    
                    messages.append({
                        "role": "tool",
                        "tool_call_id": tool_call.id,
                        "name": function_name,
                        "content": enhanced_result
                    })
                
                if processing_failed:
                    break

            elif finish_reason == "stop":
                print("\n[DONE] GPT has completed the claim processing")
                break
            else:
                print(f"\n[WARN] Unexpected finish reason: {finish_reason}")
                break
        
        if iteration >= max_iterations:
            print(f"\n[WARN] Reached maximum iterations ({max_iterations})")
            
        final_response = message.content or "Process terminated."
        claim_id = f"CLM-{datetime.now().strftime('%Y%m%d%H%M%S')}"
        
        safe_history = []
        for m in messages:
            if hasattr(m, 'model_dump'): 
                safe_history.append(m.model_dump())
            elif hasattr(m, 'dict'):
                safe_history.append(m.dict())
            elif isinstance(m, dict):
                safe_history.append(m)
            else:
                safe_history.append(str(m))

        output_data = {
            "claim_id": claim_id,
            "status": "FAILED" if processing_failed else "SUCCESS",
            "failure_reason": failure_reason,
            "document_name": document_path.name,
            "document_id": doc_id,
            "uploaded_document_ids": self.uploaded_document_ids,
            "processing_date": datetime.now().isoformat(),
            "llm_model": "gpt-4o",
            "iterations": iteration,
            "final_response": final_response,
            "conversation_history": safe_history
        }
        
        json_path = output_dir / f"{claim_id}.json"
        with open(json_path, 'w') as f:
            json.dump(output_data, f, indent=2, default=str)
            
        summary = self.generate_summary(output_data, final_response)
        if processing_failed:
            summary = f"PROCESS FAILED: {failure_reason}\n\n" + summary
            
        summary_path = output_dir / f"{claim_id}_summary.txt"
        with open(summary_path, 'w') as f:
            f.write(summary)
            
        print("\n" + "=" * 70)
        if processing_failed:
            print(f"[FAIL] Claim Processing FAILED")
            print(f"[FAIL] Error: {failure_reason}")
            print(f"[LOG] Detailed log: {json_path}")
        else:
            print(f"[SUCCESS] Claim Processing SUCCESS")
            print(f"[LOG] Saved to: {json_path}")
        print("=" * 70)
        
        return output_data
    
    def generate_summary(self, data: dict, llm_response: str) -> str:
            """Generate human-readable summary"""
            return f"""CLAIM PROCESSING SUMMARY (LLM-Driven with OpenAI)
    {'=' * 70}
    Claim ID: {data['claim_id']}
    Document: {data['document_name']}
    Document ID: {data.get('document_id', 'N/A')}
    Processed: {datetime.now().strftime('%B %d, %Y at %I:%M %p')}
    Model: {data['llm_model']}
    Status: {data['status']}
    Iterations: {data['iterations']}

    GPT's ANALYSIS AND ACTIONS
    {'-' * 70}
    {llm_response}

    {'=' * 70}
    End of Report
    """

async def main():
    project_root = Path(__file__).parent.parent
    config_file = project_root / "mcp_config.json"
    
    if not config_file.exists():
        with open(config_file, 'w') as f:
            json.dump({
                "mcpServers": {
                    "claims-server": {
                        "url": "https://localhost:9443/mcp/sse",
                        "transport": "sse"
                    }
                }
            }, f, indent=2)
            print(f"Created default config at {config_file}")

    agent = LLMClaimsAgent(str(config_file))
    
    try:
        await agent.connect_to_servers()
        
        input_dir = project_root / "input"
        output_dir = project_root / "output"
        input_dir.mkdir(exist_ok=True)
        output_dir.mkdir(exist_ok=True)
        
        pdf_files = list(input_dir.glob("*.pdf"))
        
        if not pdf_files:
            print(f"No PDF files found in {input_dir}")
            return

        for pdf_file in pdf_files:
            await agent.process_claim_with_llm(pdf_file, output_dir)
            
    finally:
        await agent.disconnect()

if __name__ == "__main__":
    asyncio.run(main())