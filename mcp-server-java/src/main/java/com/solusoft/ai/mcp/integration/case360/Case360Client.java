package com.solusoft.ai.mcp.integration.case360;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solusoft.ai.mcp.integration.case360.soap.CreateCaseFolder;
import com.solusoft.ai.mcp.integration.case360.soap.CreateCaseFolderResponse;
import com.solusoft.ai.mcp.integration.case360.soap.CreateFileStore;
import com.solusoft.ai.mcp.integration.case360.soap.CreateFileStoreResponse;
import com.solusoft.ai.mcp.integration.case360.soap.DoQueryByScriptName;
import com.solusoft.ai.mcp.integration.case360.soap.DoQueryByScriptNameResponse;
import com.solusoft.ai.mcp.integration.case360.soap.FieldPropertiesTO;
import com.solusoft.ai.mcp.integration.case360.soap.FieldPropertiesTOArray;
import com.solusoft.ai.mcp.integration.case360.soap.FmsFieldTO;
import com.solusoft.ai.mcp.integration.case360.soap.FmsRowTO;
import com.solusoft.ai.mcp.integration.case360.soap.GetCaseFolderFields;
import com.solusoft.ai.mcp.integration.case360.soap.GetCaseFolderFieldsResponse;
import com.solusoft.ai.mcp.integration.case360.soap.ObjectFactory;
import com.solusoft.ai.mcp.integration.case360.soap.PutFile;
import com.solusoft.ai.mcp.integration.case360.soap.SetCaseFolderFields;

import jakarta.xml.bind.JAXBElement;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class Case360Client {

    private final WebServiceTemplate webServiceTemplate;
    private final ObjectMapper objectMapper; // Added for JSON serialization
    private final ObjectFactory objectFactory = new ObjectFactory();
    
    // Injected ObjectMapper to handle dynamic field serialization
    public Case360Client(WebServiceTemplate webServiceTemplate, ObjectMapper objectMapper) {
        this.webServiceTemplate = webServiceTemplate;
        this.objectMapper = objectMapper;
    }

    public BigDecimal getCaseFolderTemplateId(String templateName) {
        log.info("Entering getCaseFolderTemplateId");
        log.debug("Input templateName: {}", templateName);

        try {
            var request = new DoQueryByScriptName(); // Java 10+ 'var'
            request.setQueryScriptName("getCaseTemplateIdFromName");
            
            var param = new FieldPropertiesTO();
            param.setPropertyName("TEMPLATENAME");
            param.setStringValue(templateName);
            param.setDataType(4); 
                
            var paramWrapper = new FieldPropertiesTOArray();
            paramWrapper.getFieldPropertiesTO().add(param);
            
            request.setQueryProperties(paramWrapper);
            request.getQueryProperties().getFieldPropertiesTO().add(param);
            
            JAXBElement<DoQueryByScriptName> requestElement = 
                    objectFactory.createDoQueryByScriptName(request);

            @SuppressWarnings("unchecked")
            JAXBElement<DoQueryByScriptNameResponse> responseElement = 
                (JAXBElement<DoQueryByScriptNameResponse>) webServiceTemplate.marshalSendAndReceive(requestElement);

            DoQueryByScriptNameResponse response = responseElement.getValue();

            BigDecimal result = response.getReturn().getFmsRowSetTO().getFirst().getFmsRowTO().getFirst().getFieldList().get(0).getBigDecimalValue().getValue();
            
            log.debug("Return value (Template ID): {}", result);
            return result;

        } catch (Exception e) {
            log.error("Error in getCaseFolderTemplateId for templateName: {}", templateName, e);
            throw new RuntimeException("Could not find Template ID for: " + templateName, e);
        }
    }
    
    public BigDecimal getFilestoreTemplateId(String templateName) {
        log.info("Entering getFilestoreTemplateId");
        log.debug("Input templateName: {}", templateName);

        try {
            var request = new DoQueryByScriptName();
            request.setQueryScriptName("getFileStoreTemplateId");
            
            var param = new FieldPropertiesTO();
            param.setPropertyName("TEMPLATENAME");
            param.setStringValue(templateName);
            param.setDataType(4); 
                
            var paramWrapper = new FieldPropertiesTOArray();
            paramWrapper.getFieldPropertiesTO().add(param);
            
            request.setQueryProperties(paramWrapper);
            request.getQueryProperties().getFieldPropertiesTO().add(param);
            
            JAXBElement<DoQueryByScriptName> requestElement = 
                    objectFactory.createDoQueryByScriptName(request);

            @SuppressWarnings("unchecked")
            JAXBElement<DoQueryByScriptNameResponse> responseElement = 
                (JAXBElement<DoQueryByScriptNameResponse>) webServiceTemplate.marshalSendAndReceive(requestElement);

            DoQueryByScriptNameResponse response = responseElement.getValue();

            BigDecimal result = response.getReturn().getFmsRowSetTO().getFirst().getFmsRowTO().getFirst().getFieldList().get(0).getBigDecimalValue().getValue();
            
            log.debug("Return value (FileStore Template ID): {}", result);
            return result;

        } catch (Exception e) {
            log.error("Error in getFilestoreTemplateId for templateName: {}", templateName, e);
            throw new RuntimeException("Could not find Template ID for: " + templateName, e);
        }
    }

    public String createCase(BigDecimal templateId) {
        log.info("Entering createCase");
        log.debug("Input templateId: {}", templateId);

        try {
            var request = new CreateCaseFolder();
            request.setCaseFolderTemplateId(templateId);
            
            JAXBElement<CreateCaseFolder> requestElement = 
                    objectFactory.createCreateCaseFolder(request);
            
            @SuppressWarnings("unchecked")
            JAXBElement<CreateCaseFolderResponse> responseElement = 
                (JAXBElement<CreateCaseFolderResponse>) webServiceTemplate.marshalSendAndReceive(requestElement);
            
            CreateCaseFolderResponse response = responseElement.getValue();
            String result = String.valueOf(response.getReturn());
            
            log.debug("Return value (New Case ID): {}", result);
            return result;

        } catch (Exception e) {
            log.error("Error in createCase for templateId: {}", templateId, e);
            throw e; 
        }
    }

    /**
     * Updates fields in Case360. 
     * Handles Dynamic Fields: Any key in 'updates' that does NOT exist in the case definition
     * will be bundled into a JSON string and saved to 'ADDITIONAL_DATA'.
     */
    public void updateCaseFields(String caseId, Map<String, Object> updates) {
        log.info("Entering updateCaseFields");
        log.debug("Input caseId: {}, updates: {}", caseId, updates);
        
        try {
            BigDecimal caseIdBd = new BigDecimal(caseId);

            // A. GET Existing Fields
            var getRequest = new GetCaseFolderFields();
            getRequest.setCaseFolderId(caseIdBd);
            
            JAXBElement<GetCaseFolderFields> getRequestElement = 
                    objectFactory.createGetCaseFolderFields(getRequest);
            
            @SuppressWarnings("unchecked")
            JAXBElement<GetCaseFolderFieldsResponse> getResponseElement = 
                (JAXBElement<GetCaseFolderFieldsResponse>) webServiceTemplate.marshalSendAndReceive(getRequestElement);
            
            FmsRowTO fields = getResponseElement.getValue().getReturn();
            var serverFieldList = fields.getFieldList();

            // B. TRACKING & MODIFYING
            Set<String> processedKeys = new HashSet<>();
            FmsFieldTO additionalDataField = null;
            boolean isModified = false;

            // Pass 1: Update known columns and locate the ADDITIONAL_DATA field holder
            for (FmsFieldTO field : serverFieldList) {
                String fieldName = field.getFieldName();
                
                // Keep a reference to the special field for dynamic data
                if ("ADDITIONAL_DATA".equalsIgnoreCase(fieldName)) {
                    additionalDataField = field;
                }

                if (updates.containsKey(fieldName)) {
                    applyValueToField(field, updates.get(fieldName));
                    processedKeys.add(fieldName);
                    isModified = true;
                }
            }

            // Pass 2: Handle Dynamic "Leftover" Fields
            // Create a map of fields that were NOT found in the main server list
            Map<String, Object> dynamicLeftovers = new HashMap<>(updates);
            // Remove keys we successfully processed in Pass 1
            processedKeys.forEach(dynamicLeftovers::remove);

            if (!dynamicLeftovers.isEmpty()) {
                if (additionalDataField != null) {
                    try {
                        // Serialize leftovers to JSON string
                        String jsonValue = objectMapper.writeValueAsString(dynamicLeftovers);
                        
                        log.info("Bundling {} dynamic fields into ADDITIONAL_DATA", dynamicLeftovers.size());
                        applyValueToField(additionalDataField, jsonValue);
                        isModified = true;
                        
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize dynamic fields to JSON", e);
                        // We continue, so we don't block the valid fields from saving
                    }
                } else {
                    log.warn("Dynamic fields found {} but 'ADDITIONAL_DATA' field is missing in Case360 template definition.", dynamicLeftovers.keySet());
                }
            }

            if (!isModified) {
                log.info("No fields modified for caseId: {}. Exiting updateCaseFields.", caseId);
                return; 
            }

            // C. SET (Save) back to Server
            var setRequest = new SetCaseFolderFields();
            setRequest.setCaseFolderInstanceId(caseIdBd);
            setRequest.setOriginalCaseFolderFields(fields);
            setRequest.setNewCaseFolderFields(fields);
            setRequest.setBForceUpdate(true); 
            
            JAXBElement<SetCaseFolderFields> setRequestElement = 
                    objectFactory.createSetCaseFolderFields(setRequest);
            
            webServiceTemplate.marshalSendAndReceive(setRequestElement);
            
            log.info("Exiting updateCaseFields successfully");

        } catch (Exception e) {
            log.error("Error in updateCaseFields for caseId: {}", caseId, e);
            throw e;
        }
    }

    
    private void applyValueToField(FmsFieldTO field, Object value) {
        field.setModified(true);
        field.setNullValue(false);

        // Java 21 Pattern Matching for Switch
        switch (value) {
            case String s -> {
                field.setStringValue(s);
                // Resetting other potential types (Case360 usually requires nulling the others)
                field.setBigDecimalValue(null); 
            }
            case BigDecimal bd -> 
                field.setBigDecimalValue(objectFactory.createFmsFieldTOBigDecimalValue(bd));
            case Integer i -> 
                field.setBigDecimalValue(objectFactory.createFmsFieldTOBigDecimalValue(BigDecimal.valueOf(i)));
            case Double d -> 
                field.setBigDecimalValue(objectFactory.createFmsFieldTOBigDecimalValue(BigDecimal.valueOf(d)));
            case Boolean b -> 
                field.setStringValue(String.valueOf(b)); // Defaulting boolean to String "true"/"false"
            case null -> 
                field.setNullValue(true);
            default -> 
                field.setStringValue(value.toString());
        }
    }
    
    public String createFileStore(BigDecimal templateId) {
        log.info("Entering createFileStore");
        log.debug("Input templateId: {}", templateId);
        
        try {
            var request = new CreateFileStore();
            request.setTemplateId(templateId);
            
            JAXBElement<CreateFileStore> requestElement = 
                    objectFactory.createCreateFileStore(request);
            
            @SuppressWarnings("unchecked")
            JAXBElement<CreateFileStoreResponse> responseElement  = 
                (JAXBElement<CreateFileStoreResponse>) webServiceTemplate.marshalSendAndReceive(requestElement);
            
            CreateFileStoreResponse response = responseElement.getValue();
            String result = String.valueOf(response.getReturn());
            
            log.debug("Return value (FileStore ID): {}", result);
            return result;

        } catch (Exception e) {
            log.error("Error in createFileStore for templateId: {}", templateId, e);
            throw new RuntimeException("Could not create filestore instance for: " + templateId, e);
        }
    }
    
    public void uploadDocument(BigDecimal docId, byte[] content, String fileName) {
        log.info("Entering uploadDocument");
        log.debug("Input docId: {}, fileName: {}, contentSize: {}", docId, fileName, (content != null ? content.length : 0));
        
        try {
            var request = new PutFile();
            request.setData(content);
            request.setDocumentId(docId);
            request.setFileName(fileName);
            
            JAXBElement<PutFile> requestElement = 
                    objectFactory.createPutFile(request);
            
            webServiceTemplate.marshalSendAndReceive(requestElement);
            
            log.info("Exiting uploadDocument successfully");

        } catch (Exception e) {
            log.error("Error in uploadDocument for docId: {}", docId, e);
            throw e;
        }
    }
}