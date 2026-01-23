package com.solusoft.ai.mcp.integration.case360;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.Map;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;

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

    public String getClaimStatus(String claimId) {
        log.info("Entering getClaimStatus");
        log.debug("Input claimId: {} ", claimId);

        try {
        	
        	if(claimId==null || claimId.isEmpty()) {
				throw new Case360IntegrationException("Error in getClaimStatus: claimId is null or empty");
			}
        	
        	String queryScript = "getHCClaimByClaimId";
        	
        	if(claimId.toUpperCase().startsWith("AUTO")) {
				queryScript = "getMotorClaimByClaimId";
        	} 
        	
            var request = new DoQueryByScriptName(); // Java 10+ 'var'
            request.setQueryScriptName(queryScript);
            
            var param = new FieldPropertiesTO();
            param.setPropertyName("CLAIM_ID");
            param.setStringValue(claimId);
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

            String result = response.getReturn().getFmsRowSetTO().getFirst().getFmsRowTO().getFirst().getFieldList().stream()
					.filter(field -> "CLAIM_STATUS".equals(field.getFieldName()))
					.findFirst()
					.map(FmsFieldTO::getStringValue)
					.orElse(null);
            
            log.debug("Return value (Status): {}", result);
            log.info("Exiting getClaimStatus successfully");
            return result;

        }   catch (Exception e) {
            log.error("Error in getClaimStatus for claimId : {}", claimId, e);
            throw new Case360IntegrationException("Case360 Operation getClaimStatus Failed for: " + claimId, e);
        }
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
        log.info("Input caseId: {}", strCaseId);
        
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

            DatatypeFactory datatypeFactory;
            try {
                datatypeFactory = DatatypeFactory.newInstance();
            } catch (DatatypeConfigurationException e) {
                throw new RuntimeException("DatatypeFactory init failed", e);
            }
            ZoneId zoneId = ZoneId.systemDefault();


            for (FmsFieldTO field : newFields.getFieldList()) {
                String fieldName = field.getFieldName();

                if (updates.containsKey(fieldName)) {
                    Object value = updates.get(fieldName);

                    if (value != null) {
                        field.setModified(true);
                        field.setNullValue(false);
                        
                        switch (field.getDataType()) {
                            case 4 -> { 
                                field.setStringValue(String.valueOf(value));
                                field.setBigDecimalValue(null);
                            }
                            case 5 -> { 
                                if (value instanceof java.time.LocalDate localDate) {
                                    ZonedDateTime zdt = localDate.atStartOfDay(zoneId);
                                    field.setCalendarValue(datatypeFactory.newXMLGregorianCalendar(GregorianCalendar.from(zdt)));
                                }
                            }
                            case 2 -> field.setIntValue(Integer.valueOf(value.toString()));
                            case 1 -> field.setBooleanValue(Boolean.valueOf(value.toString()));
                            case 6 -> field.setBigDecimalValue(objectFactory.createFmsFieldTOBigDecimalValue(new BigDecimal(value.toString())));
                            default -> field.setStringValue(value.toString());
                        }
                    }
                } 
            }


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