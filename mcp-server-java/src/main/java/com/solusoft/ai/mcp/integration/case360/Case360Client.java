package com.solusoft.ai.mcp.integration.case360;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solusoft.ai.mcp.exception.Case360IntegrationException;
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

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

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

        }   catch (Exception e) {
            log.error("Error in getCaseFolderTemplateId for templateName: {}", templateName, e);
            throw new Case360IntegrationException("Case360 Query Failed for: " + templateName, e);
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
            throw new Case360IntegrationException("Case360 Query Failed for: " + templateName, e);
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
            throw new Case360IntegrationException("Filestore creation failed for Template ID : " + templateId, e); 
        }
    }

    /**
     * Updates fields in Case360. 
     * Handles Dynamic Fields: Any key in 'updates' that does NOT exist in the case definition
     * will be bundled into a JSON string and saved to 'ADDITIONAL_DATA'.
     */
    public void updateCaseFields(String strCaseId, Map<String, Object> updates) {
        log.info("Entering updateCaseFields");
        log.info("Input caseId: {}, updates: {}", strCaseId, updates);
        
        try {
            BigDecimal caseId = new BigDecimal(strCaseId);

            // A. GET Existing Fields
            var getRequest = new GetCaseFolderFields();
            getRequest.setCaseFolderId(caseId);
            
            JAXBElement<GetCaseFolderFields> getRequestElement = 
                    objectFactory.createGetCaseFolderFields(getRequest);
            
            @SuppressWarnings("unchecked")
            JAXBElement<GetCaseFolderFieldsResponse> getResponseElement = 
                (JAXBElement<GetCaseFolderFieldsResponse>) webServiceTemplate.marshalSendAndReceive(getRequestElement);
            
            FmsRowTO fields = getResponseElement.getValue().getReturn();
            FmsRowTO newFields = getResponseElement.getValue().getReturn();

            // B. TRACKING & MODIFYING
            Set<String> processedKeys = new HashSet<>();
            FmsFieldTO additionalDataField = null;
            
            
            for(FmsFieldTO field:newFields.getFieldList()) {
            	if ("ADDITIONAL_DATA".equalsIgnoreCase(field.getFieldName())) {
                    additionalDataField = field;
                }
            	
				if(updates.containsKey(field.getFieldName())) {
					Object value = updates.get(field.getFieldName());
					processedKeys.add(field.getFieldName());
					if(value!=null) {
						field.setModified(true);
						field.setNullValue(false);
						switch (field.getDataType()) {
			            case 4 -> {
			                field.setStringValue(String.valueOf(value));
			                // Resetting other potential types (Case360 usually requires nulling the others)
			                field.setBigDecimalValue(null); 
			            }
			            case 5 -> {
			            	if (value instanceof java.time.LocalDate) {
			            		LocalDate localDate =   LocalDate.parse(value.toString()) ;
			            		ZonedDateTime zdt = localDate.atStartOfDay(ZoneId.systemDefault());
				            	field.setCalendarValue(DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(zdt)));
			            	}
			            	
			            }
			            case 2 -> 
		                	field.setIntValue(Integer.valueOf(value.toString()));
			            case 1 -> 
		                	field.setBooleanValue(Boolean.valueOf(value.toString()));
			            case 6 -> 
			                field.setBigDecimalValue(objectFactory.createFmsFieldTOBigDecimalValue(new BigDecimal(value.toString())));
			            default -> 
			                field.setStringValue(value.toString());
						}
					}
				} else {
					 additionalDataField = field;
				}
            	
            }
            /*
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
 
            */
            // Pass 2: Handle Dynamic "Leftover" Fields
            // Create a map of fields that were NOT found in the main server list
            Map<String, Object> additionalFields = new HashMap<>(updates);
            // Remove keys we successfully processed in Pass 1
            processedKeys.forEach(additionalFields::remove);
           
            if (!additionalFields.isEmpty()) {
                if (additionalDataField != null) {
                    try {
                        // Serialize leftovers to JSON string
                        String jsonValue = objectMapper.writeValueAsString(additionalFields);
                        additionalDataField.setStringValue(jsonValue);
                        additionalDataField.setModified(true);
                        additionalDataField.setNullValue(false);
                        log.info("Bundling {} dynamic fields into ADDITIONAL_DATA", additionalFields.size());
                        
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize dynamic fields to JSON", e);
                        // We continue, so we don't block the valid fields from saving
                    }
                } else {
                    log.warn("Dynamic fields found {} but 'ADDITIONAL_DATA' field is missing in Case360 template definition.", additionalFields.keySet());
                }
            }

            // C. SET (Save) back to Server
            var setRequest = new SetCaseFolderFields();
      	  	setRequest.setCaseFolderInstanceId(caseId);
            setRequest.setOriginalCaseFolderFields(fields);
            setRequest.setNewCaseFolderFields(newFields);
            setRequest.setBForceUpdate(true); 
            
            JAXBElement<SetCaseFolderFields> setRequestElement = 
                    objectFactory.createSetCaseFolderFields(setRequest);
            
            webServiceTemplate.marshalSendAndReceive(setRequestElement);
            
            log.info("Exiting updateCaseFields successfully");

        }  catch (Exception e) {
        	throw new Case360IntegrationException("Fields update failed for Case ID: " + strCaseId, e);
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

        }  catch (Exception e) {
            log.error("Error in createFileStore for templateId: {}", templateId, e);
            throw new Case360IntegrationException("Filestore creation failed for Template ID : " + templateId, e);
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

        }  catch (Exception e) {
            log.error("Error in uploadDocument for docId: {}", docId, e);
            throw new Case360IntegrationException("Upload failed for Document ID: " + docId, e);
        }
    }

    public XMLGregorianCalendar stringToGregorian(String dateString) throws DatatypeConfigurationException {
        // 1. Define your format (e.g., yyyy-MM-dd)
    	if(dateString!=null && !dateString.isEmpty()) {
	        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	
	        // 2. Parse to LocalDate (or LocalDateTime if you have time info)
	        LocalDate localDate = LocalDate.parse(dateString, formatter);
	
	        // 3. Convert to ZonedDateTime (Required for GregorianCalendar)
	        ZonedDateTime zdt = localDate.atStartOfDay(ZoneId.systemDefault());
	
	        // 4. Create GregorianCalendar directly
	        
	        return DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(zdt));
    	} 
    	return null;
    }
}