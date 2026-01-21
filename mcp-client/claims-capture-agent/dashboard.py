import streamlit as st
import psycopg2
import pandas as pd
import time
import os

# Configuration
st.set_page_config(page_title="Case360 Operations Dashboard", page_icon="📊", layout="wide")

# --- POSTGRESQL CONFIGURATION ---
# Check your docker-compose or .env for these values
DB_HOST = "localhost"
DB_PORT = "5432" 
DB_NAME = "mcp_db"
DB_USER = "mcp_user"      # Updated to standard default
DB_PASS = "secret_password" # Update this to match your docker-compose.yml

# Custom CSS
st.markdown("""
<style>
    .metric-card {background-color: #f0f2f6; padding: 20px; border-radius: 10px; border-left: 5px solid #4e8cff;}
    .stDataFrame {border: 1px solid #e0e0e0; border-radius: 5px;}
</style>
""", unsafe_allow_html=True)

def get_connection():
    """Connect to the PostgreSQL database"""
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            port=DB_PORT,
            database=DB_NAME,
            user=DB_USER,
            password=DB_PASS
        )
        return conn
    except Exception as e:
        st.sidebar.error(f"❌ Connection Failed: {e}")
        return None

def load_data():
    """Fetch latest claims from Postgres"""
    conn = get_connection()
    if not conn:
        return pd.DataFrame()
    
    # --- UPDATED QUERY FOR NEW JSONB SCHEMA ---
    # We use the ->> operator to extract fields from the 'additional_data' JSON column
    # so they appear as regular columns in our DataFrame.
    query = """
    SELECT 
        id,
        claim_id, 
        claimant_name, 
        claim_type, 
        claim_amount, 
        status, 
        created_at,
        policy_number,
        case_id,
        -- Extract Dynamic JSON Fields
        additional_data->>'hospital' as hospital,
        additional_data->>'diagnosis' as diagnosis,
        additional_data->>'vehicle_make' as vehicle_make,
        additional_data->>'vehicle_model' as vehicle_model,
        additional_data->>'vehicle_year' as vehicle_year,
        additional_data->>'incident_type' as incident_type,
        additional_data->>'workflow_id' as workflow_id
    FROM claims 
    ORDER BY created_at DESC
    """
    
    try:
        df = pd.read_sql_query(query, conn)
        conn.close()
    except Exception as e:
        st.error(f"Error reading data: {e}")
        if conn: conn.close()
        return pd.DataFrame()

    if not df.empty and 'claim_amount' in df.columns:
        # Postgres returns 'Decimal' types for NUMERIC columns.
        # We convert to float for Streamlit display.
        df['claim_amount'] = pd.to_numeric(df['claim_amount'], errors='coerce').fillna(0.0)

    return df

# --- SIDEBAR ---
with st.sidebar:
    st.image("https://cdn-icons-png.flaticon.com/512/2666/2666505.png", width=50)
    st.title("Ops Dashboard")
    
    status_indicator = st.empty()
    if get_connection():
        status_indicator.success("DB Connected")
    else:
        status_indicator.error("DB Disconnected")

    if st.button("🔄 Refresh Data"):
        st.rerun()
        
    st.divider()
    auto_refresh = st.checkbox("Auto-Refresh (5s)", value=False)

# --- MAIN PAGE ---
st.title("📊 Claims Processing Live Feed")
st.markdown("Real-time monitoring of the autonomous Agent backend (PostgreSQL).")

# Load Data
df = load_data()

if df.empty:
    st.warning("No claims found in database yet. Check if the Docker Container is running.")
else:
    # 1. TOP METRICS
    col1, col2, col3, col4 = st.columns(4)
    
    total_claims = len(df)
    total_amount = df['claim_amount'].sum()
    
    # Normalize strings for case-insensitive comparison (AUTO vs Auto vs auto)
    # Using 'str.contains' with regex=False is safer for partial matches, 
    # but exact match with lower() is better here.
    motor_claims = len(df[df['claim_type'].str.lower() == 'auto'])
    health_claims = len(df[df['claim_type'].str.lower() == 'health'])
    
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
        # Handle cases where claim_type might be None
        unique_types = [x for x in df['claim_type'].unique() if x is not None]
        type_filter = st.multiselect("Filter by Type", options=unique_types, default=unique_types)
    
    # Apply filter
    if type_filter:
        filtered_df = df[df['claim_type'].isin(type_filter)]
    else:
        filtered_df = df
    
    st.dataframe(
        filtered_df[['created_at', 'claim_id', 'claimant_name', 'claim_type', 'claim_amount', 'status']],
        width='stretch',
        column_config={
            "created_at": st.column_config.DatetimeColumn("Processed At", format="D MMM, HH:mm"),
            "claim_amount": st.column_config.NumberColumn("Amount", format="$%.2f")
        },
        hide_index=True
    )
    
    # 3. DETAILS VIEW
    st.divider()
    st.subheader("🔎 Claim Inspector")
    
    selected_claim_id = st.selectbox("Select Claim ID to View Details", options=df['claim_id'])
    
    if selected_claim_id:
        record = df[df['claim_id'] == selected_claim_id].iloc[0]
        
        d_col1, d_col2 = st.columns(2)
        
        with d_col1:
            st.info("📂 **Case Info**")
            st.write(f"**Claimant:** {record['claimant_name']}")
            st.write(f"**Policy:** {record['policy_number']}")
            # Handle missing workflow_id gracefully
            wf_id = record['workflow_id'] if record['workflow_id'] else "N/A"
            st.write(f"**Workflow ID:** `{wf_id}`")
            st.write(f"**Case ID:** `{record['case_id']}`")
            st.write(f"**Status:** `{record['status']}`")
            
        with d_col2:
            is_motor = str(record['claim_type']).upper() == 'AUTO'
            
            st.success("🚗 **Incident Details**" if is_motor else "🏥 **Medical Details**")
            
            if is_motor:
                # Use .get() approach via Pandas by checking if isnull
                make = record['vehicle_make'] if pd.notna(record['vehicle_make']) else "Unknown"
                model = record['vehicle_model'] if pd.notna(record['vehicle_model']) else ""
                year = record['vehicle_year'] if pd.notna(record['vehicle_year']) else ""
                
                st.write(f"**Vehicle:** {year} {make} {model}")
                st.write(f"**Incident:** {record['incident_type']}")
            else:
                st.write(f"**Hospital:** {record['hospital']}")
                st.write(f"**Diagnosis:** {record['diagnosis']}")

# Auto-refresh logic
if auto_refresh:
    time.sleep(5)
    st.rerun()