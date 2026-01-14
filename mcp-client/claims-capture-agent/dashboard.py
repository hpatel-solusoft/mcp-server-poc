import streamlit as st
import sqlite3
import pandas as pd
import json
from pathlib import Path
import time

# Configuration
st.set_page_config(page_title="Case360 Operations Dashboard", page_icon="📊", layout="wide")
from pathlib import Path

# OLD: DB_FILE = Path("data/claims.db")

# NEW: Paste the full path from your Server project here
# Example (Update this to your actual path!):
DB_FILE = Path(r"D:\Solusoft\AI\MCP\my_work\claims-mcp-server\src\data\claims.db")

# Custom CSS
st.markdown("""
<style>
    .metric-card {background-color: #f0f2f6; padding: 20px; border-radius: 10px; border-left: 5px solid #4e8cff;}
    .stDataFrame {border: 1px solid #e0e0e0; border-radius: 5px;}
</style>
""", unsafe_allow_html=True)

def get_connection():
    """Connect to the shared SQLite database"""
    if not DB_FILE.exists():
        return None
    return sqlite3.connect(DB_FILE)

def load_data():
    """Fetch latest claims from DB"""
    conn = get_connection()
    if not conn:
        return pd.DataFrame()
    
    query = """
    SELECT 
        claim_id, 
        claimant_name, 
        claim_type, 
        claim_amount, 
        status, 
        created_at,
        policy_number,
        workflow_id
    FROM claims 
    ORDER BY created_at DESC
    """
    df = pd.read_sql_query(query, conn)
    conn.close()
    return df

# --- SIDEBAR ---
with st.sidebar:
    st.image("https://cdn-icons-png.flaticon.com/512/2666/2666505.png", width=50)
    st.title("Ops Dashboard")
    
    if st.button("🔄 Refresh Data"):
        st.rerun()
        
    st.divider()
    auto_refresh = st.checkbox("Auto-Refresh (5s)", value=False)

# --- MAIN PAGE ---
st.title("📊 Claims Processing Live Feed")
st.markdown("Real-time monitoring of the autonomous Agent backend.")

# Load Data
df = load_data()

if df.empty:
    st.warning("No claims found in database yet. Start the Watcher and drop a file in 'input/'.")
else:
    # 1. TOP METRICS
    col1, col2, col3, col4 = st.columns(4)
    
    total_claims = len(df)
    total_amount = df['claim_amount'].sum()
    motor_claims = len(df[df['claim_type'] == 'motor'])
    health_claims = len(df[df['claim_type'] == 'healthcare'])
    
    col1.metric("Total Processed", total_claims)
    col2.metric("Total Value", f"${total_amount:,.2f}")
    col3.metric("Motor Claims", motor_claims)
    col4.metric("Health Claims", health_claims)
    
    st.divider()
    
    # 2. DATA TABLE
    st.subheader("Recent Transactions")
    
    # Simple filters
    filter_col1, filter_col2 = st.columns(2)
    with filter_col1:
        type_filter = st.multiselect("Filter by Type", options=df['claim_type'].unique(), default=df['claim_type'].unique())
    
    # Apply filter
    filtered_df = df[df['claim_type'].isin(type_filter)]
    
    # Style the status column
    def color_status(val):
        color = 'green' if val == 'submitted_to_case360' else 'red'
        return f'color: {color}'

    st.dataframe(
        filtered_df,
        use_container_width=True,
        column_config={
            "created_at": st.column_config.DatetimeColumn("Processed At", format="D MMM, HH:mm"),
            "claim_amount": st.column_config.NumberColumn("Amount", format="$%.2f")
        },
        hide_index=True
    )
    
    # 3. DETAILS VIEW
    st.divider()
    st.subheader("🔎 Claim Inspector")
    
    selected_claim = st.selectbox("Select Claim ID to View Details", options=df['claim_id'])
    
    if selected_claim:
        conn = get_connection()
        record = pd.read_sql_query(f"SELECT * FROM claims WHERE claim_id = '{selected_claim}'", conn).iloc[0]
        conn.close()
        
        d_col1, d_col2 = st.columns(2)
        
        with d_col1:
            st.info("📂 **Case Info**")
            st.write(f"**Claimant:** {record['claimant_name']}")
            st.write(f"**Policy:** {record['policy_number']}")
            st.write(f"**Workflow ID:** `{record['workflow_id']}`")
            st.write(f"**Case ID:** `{record['case_id']}`")
            
        with d_col2:
            st.success("🚗 **Incident Details**" if record['claim_type'] == 'motor' else "🏥 **Medical Details**")
            if record['claim_type'] == 'motor':
                st.write(f"**Vehicle:** {record['vehicle_year']} {record['vehicle_make']} {record['vehicle_model']}")
                st.write(f"**Incident:** {record['incident_type']}")
            else:
                st.write(f"**Hospital:** {record['hospital']}")
                st.write(f"**Diagnosis:** {record['diagnosis']}")

# Auto-refresh logic
if auto_refresh:
    time.sleep(5)
    st.rerun()