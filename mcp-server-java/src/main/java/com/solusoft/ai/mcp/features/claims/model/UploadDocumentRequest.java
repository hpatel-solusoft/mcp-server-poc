package com.solusoft.ai.mcp.features.claims.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Input for uploading a supporting document")
public record UploadDocumentRequest(
    @JsonPropertyDescription("The name of the file (e.g., invoice.pdf)")
    String fileName,
    
    @JsonPropertyDescription("The Base64 encoded content of the file")
    String fileContentBase64
) {}