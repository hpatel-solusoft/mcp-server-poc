package com.solusoft.ai.mcp.integration.case360;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;
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
import com.solusoft.ai.mcp.integration.case360.soap.PutFileResponse;
import com.solusoft.ai.mcp.integration.case360.soap.SetCaseFolderFields;
import com.solusoft.ai.mcp.integration.case360.soap.SetCaseFolderFieldsResponse;

import jakarta.xml.bind.JAXBElement;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class Case360Client {

    private final WebServiceTemplate webServiceTemplate;
    private final ObjectFactory objectFactory = new ObjectFactory();
    
    public Case360Client(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    public BigDecimal getCaseFolderTemplateId(String templateName) {
        log.info("Entering getCaseFolderTemplateId");
        log.debug("Input templateName: {}", templateName);

        try {
            DoQueryByScriptName request = new DoQueryByScriptName();
            request.setQueryScriptName("getCaseTemplateIdFromName");
            
            FieldPropertiesTO param = new FieldPropertiesTO();
            param.setPropertyName("TEMPLATENAME");
            param.setStringValue(templateName);
            param.setDataType(4); // 4 = String in Case360
                
            FieldPropertiesTOArray paramWrapper = new FieldPropertiesTOArray();
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
            log.info("Exiting getCaseFolderTemplateId");
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
            // Create Request Object
            DoQueryByScriptName request = new DoQueryByScriptName();
            request.setQueryScriptName("getFileStoreTemplateId");
            
            FieldPropertiesTO param = new FieldPropertiesTO();
            param.setPropertyName("TEMPLATENAME");
            param.setStringValue(templateName);
            param.setDataType(4); // 4 = String in Case360
                
            FieldPropertiesTOArray paramWrapper = new FieldPropertiesTOArray();
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
            log.info("Exiting getFilestoreTemplateId");
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
            CreateCaseFolder request = new CreateCaseFolder();
            request.setCaseFolderTemplateId(templateId);
            
            JAXBElement<CreateCaseFolder> requestElement = 
                    objectFactory.createCreateCaseFolder(request);
            
            
            @SuppressWarnings("unchecked")
            JAXBElement<CreateCaseFolderResponse> responseElement = 
                (JAXBElement<CreateCaseFolderResponse>) webServiceTemplate.marshalSendAndReceive(requestElement);
            CreateCaseFolderResponse response = responseElement.getValue();

            // The response is usually the new Case ID (BigDecimal or String)
            String result = String.valueOf(response.getReturn());
            
            log.debug("Return value (New Case ID): {}", result);
            log.info("Exiting createCase");
            return result;

        } catch (Exception e) {
            log.error("Error in createCase for templateId: {}", templateId, e);
            throw e; // Rethrowing since original code didn't catch explicitly, but logging ensures visibility
        }
    }

    public void updateCaseFields(String caseId, Map<String, Object> updates) {
        log.info("Entering updateCaseFields");
        log.debug("Input caseId: {}, updates: {}", caseId, updates);
        
        try {
            BigDecimal caseIdBd = new BigDecimal(caseId);

            // A. GET Existing Fields
            GetCaseFolderFields getRequest = new GetCaseFolderFields();
            getRequest.setCaseFolderId(caseIdBd);
            
            
            JAXBElement<GetCaseFolderFields> getRequestElement = 
                    objectFactory.createGetCaseFolderFields(getRequest);
            
            
            @SuppressWarnings("unchecked")
            JAXBElement<GetCaseFolderFieldsResponse> getResponseElement = 
                (JAXBElement<GetCaseFolderFieldsResponse>) webServiceTemplate.marshalSendAndReceive(getRequestElement);
            
            GetCaseFolderFieldsResponse getResponse = getResponseElement.getValue();
            
            FmsRowTO fields = getResponse.getReturn();

            // B. MODIFY Fields (Memory only)
            // We iterate through the retrieved fields and update matches
            boolean isModified = false;
            for (FmsFieldTO field : fields.getFieldList()) {
                if (updates.containsKey(field.getFieldName())) {
                    Object newValue = updates.get(field.getFieldName());
                    applyValueToField(field, newValue);
                    isModified = true;
                }
            }

            if (!isModified) {
                log.info("No fields modified for caseId: {}. Exiting updateCaseFields.", caseId);
                return; // Nothing to save
            }

            // C. SET (Save) back to Server
            SetCaseFolderFields setRequest = new SetCaseFolderFields();
            setRequest.setCaseFolderInstanceId(caseIdBd);
            setRequest.setOriginalCaseFolderFields(fields); // Optimistic locking
            setRequest.setNewCaseFolderFields(fields);      // The modified object
            setRequest.setBForceUpdate(true);               // Force save
            
            JAXBElement<SetCaseFolderFields> setRequestElement = 
                    objectFactory.createSetCaseFolderFields(setRequest);
            
            @SuppressWarnings("unchecked")
            JAXBElement<SetCaseFolderFieldsResponse> setResponseElement = 
                (JAXBElement<SetCaseFolderFieldsResponse>) webServiceTemplate.marshalSendAndReceive(setRequestElement);
            
            SetCaseFolderFieldsResponse setResponse = setResponseElement.getValue();
            
            log.info("Exiting updateCaseFields successfully");

        } catch (Exception e) {
            log.error("Error in updateCaseFields for caseId: {}", caseId, e);
            throw e;
        }
    }

    private void applyValueToField(FmsFieldTO field, Object value) {
        // No heavy logging here to avoid spamming logs per field, but safely catching issues is good practice
        try {
            field.setModified(true);
            field.setNullValue(false);

            // Logic matched from your Python 'update_field_values' method
            if (value instanceof String) {
                field.setStringValue((String) value);
                field.setBigDecimalValue(null);
                // In a real app, you might check field.getDataType() == 4
            } else if (value instanceof BigDecimal) {
                // Case360 uses BigDecimal for numbers
                field.setBigDecimalValue(objectFactory.createFmsFieldTOBigDecimalValue(new BigDecimal(value.toString())));
            }
            // Add Date logic here if needed
        } catch (Exception e) {
            log.error("Error applying value to field: {} with value: {}", field.getFieldName(), value, e);
            throw e;
        }
    }
    
    public String createFileStore(BigDecimal templateId) {
        log.info("Entering createFileStore");
        log.debug("Input templateId: {}", templateId);
        
        try {
            CreateFileStore request = new CreateFileStore();
            request.setTemplateId(templateId);
            
            JAXBElement<CreateFileStore> requestElement = 
                    objectFactory.createCreateFileStore(request);
            
            @SuppressWarnings("unchecked")
            JAXBElement<CreateFileStoreResponse> responseElement  = 
                (JAXBElement<CreateFileStoreResponse>) webServiceTemplate.marshalSendAndReceive(requestElement);
            
            CreateFileStoreResponse response = responseElement.getValue();
            String result = String.valueOf(response.getReturn());
            
            log.debug("Return value (FileStore ID): {}", result);
            log.info("Exiting createFileStore");
            return result;

        } catch (Exception e) {
            log.error("Error in createFileStore for templateId: {}", templateId, e);
            throw new RuntimeException("Could not create filestore instance for: " + templateId, e);
        }
    }
    
    public void uploadDocument(BigDecimal docId, byte[] content, String fileName) {
        log.info("Entering uploadDocument");
        // Avoiding logging raw byte[] content content, logging size instead
        log.debug("Input docId: {}, fileName: {}, contentSize: {}", docId, fileName, (content != null ? content.length : 0));
        
        try {
            PutFile request = new PutFile();
            request.setData(content);
            request.setDocumentId(docId);
            request.setFileName(fileName);
            
            JAXBElement<PutFile> requestElement = 
                    objectFactory.createPutFile(request);
            
            @SuppressWarnings("unchecked")
            JAXBElement<PutFileResponse> responseElement  = 
                (JAXBElement<PutFileResponse>) webServiceTemplate.marshalSendAndReceive(requestElement);
            
            PutFileResponse setResponse = responseElement.getValue();
            
            log.info("Exiting uploadDocument successfully");

        } catch (Exception e) {
            log.error("Error in uploadDocument for docId: {}", docId, e);
            throw e;
        }
    }
}