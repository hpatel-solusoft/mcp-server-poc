import time
import shutil
import asyncio
import sys
import logging
from pathlib import Path
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler

# Add src to path to import your Agent
sys.path.append(str(Path(__file__).parent))

# --- IMPORT FIX ---
try:
    from src.agent_llm_auto_http import LLMClaimsAgent
except ImportError as e:
    print(f"❌ Detailed Error: {e}")
    sys.exit(1)

# Configuration
INPUT_DIR = Path("input")
PROCESSED_DIR = Path("processed")
ERROR_DIR = Path("errors")
OUTPUT_DIR = Path("output")
# REMOVED: SERVER_URL = "http://localhost:8000/sse" 
# ADDED: Config path
CONFIG_PATH = Path("mcp_config.json")

# Setup Logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger("Watcher")

class ClaimHandler(FileSystemEventHandler):
    """Handles file system events for the input directory"""
    
    def on_created(self, event):
        # Ignore directories and non-pdf files
        if event.is_directory or not event.src_path.lower().endswith('.pdf'):
            return
            
        # Small delay to ensure file copy is complete
        time.sleep(1)
        self.process_file(Path(event.src_path))

    def process_file(self, file_path: Path):
        logger.info(f"👀 Detected new file: {file_path.name}")
        
        # Run the async agent in a synchronous wrapper
        try:
            asyncio.run(self._run_agent(file_path))
            
            # Move to processed
            shutil.move(str(file_path), str(PROCESSED_DIR / file_path.name))
            logger.info(f"✅ Success! Moved {file_path.name} to 'processed/'")
            
        except Exception as e:
            logger.error(f"❌ Failed to process {file_path.name}: {e}")
            # Move to error
            shutil.move(str(file_path), str(ERROR_DIR / file_path.name))
            logger.info(f"⚠️ Moved {file_path.name} to 'errors/'")

    async def _run_agent(self, file_path: Path):
        # FIX: Instantiate with config_path instead of server_url
        agent = LLMClaimsAgent(config_path=str(CONFIG_PATH))
        try:
            logger.info("   🔌 Connecting to Agent...")
            # Note: connect_to_mcp_server changed to connect_to_servers in your new code
            if hasattr(agent, 'connect_to_servers'):
                await agent.connect_to_servers()
            else:
                await agent.connect_to_mcp_server()
            
            logger.info("   🤖 Agent processing...")
            await agent.process_claim_with_llm(file_path, OUTPUT_DIR)
            
        finally:
            await agent.disconnect()

def main():
    # Create directories if they don't exist
    for p in [INPUT_DIR, PROCESSED_DIR, ERROR_DIR, OUTPUT_DIR]:
        p.mkdir(exist_ok=True)
    
    # Check for config
    if not CONFIG_PATH.exists():
        logger.error(f"❌ Config file not found: {CONFIG_PATH.absolute()}")
        logger.error("Please create mcp_config.json in the project root.")
        sys.exit(1)

    event_handler = ClaimHandler()
    observer = Observer()
    observer.schedule(event_handler, str(INPUT_DIR), recursive=False)
    
    observer.start()
    logger.info(f"🚀 Watcher Service Started")
    logger.info(f"📂 Monitoring: {INPUT_DIR.absolute()}")
    logger.info("   (Press Ctrl+C to stop)")
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
        logger.info("🛑 Watcher stopped")
    
    observer.join()

if __name__ == "__main__":
    main()