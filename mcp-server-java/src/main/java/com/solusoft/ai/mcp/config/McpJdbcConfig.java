package com.solusoft.ai.mcp.config;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.dialect.PostgresDialect;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Configuration
public class McpJdbcConfig extends AbstractJdbcConfiguration {

    // 1. Explicitly set Postgres Dialect to prevent "SelectRenderContext" errors
    @Override
    public Dialect jdbcDialect(NamedParameterJdbcOperations operations) {
        return PostgresDialect.INSTANCE;
    }

    // 2. Register the converter
    @Override
    protected List<?> userConverters() {
        return Arrays.asList(new StringToJsonbConverter());
    }

    // 3. The Converter: Intercepts String -> DB and casts to JSONB
    @WritingConverter
    static class StringToJsonbConverter implements Converter<String, PGobject> {
        @Override
        public PGobject convert(String source) {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb"); // <--- This fixes your error
            try {
                jsonObject.setValue(source);
            } catch (SQLException e) {
                throw new IllegalArgumentException("Failed to convert String to JSONB", e);
            }
            return jsonObject;
        }
    }
}