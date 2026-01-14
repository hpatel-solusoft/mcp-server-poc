package com.solusoft.ai.mcp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.relational.core.dialect.LockClause;
import org.springframework.data.relational.core.sql.LockOptions;
import org.springframework.data.relational.core.sql.render.SelectRenderContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Configuration
public class SQLiteConfig extends AbstractJdbcConfiguration {

    @Override
    public Dialect jdbcDialect(NamedParameterJdbcOperations operations) {
        return new SQLiteDialect();
    }

    /**
     * Minimal Dialect implementation for SQLite.
     * Handles LIMIT/OFFSET and suppresses unsupported Locking (FOR UPDATE).
     */
    static class SQLiteDialect implements Dialect {

        @Override
        public LimitClause limit() {
            return new LimitClause() {
                @Override
                public String getLimit(long limit) {
                    return "LIMIT " + limit;
                }

                @Override
                public String getOffset(long offset) {
                    return "OFFSET " + offset;
                }

                @Override
                public String getLimitOffset(long limit, long offset) {
                    return "LIMIT " + limit + " OFFSET " + offset;
                }

                @Override
                public Position getClausePosition() {
                    return Position.AFTER_ORDER_BY;
                }
            };
        }

        @Override
        public LockClause lock() {
            // SQLite does not support "SELECT ... FOR UPDATE" row locking
            return new LockClause() {
                @Override
                public String getLock(LockOptions lockOptions) {
                    return "";
                }

                @Override
                public Position getClausePosition() {
                    return Position.AFTER_ORDER_BY;
                }
            };
        }

		@Override
		public SelectRenderContext getSelectContext() {
			// TODO Auto-generated method stub
			return null;
		}
    }
}