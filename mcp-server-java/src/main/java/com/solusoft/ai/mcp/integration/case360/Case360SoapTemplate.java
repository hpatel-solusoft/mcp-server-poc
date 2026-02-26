package com.solusoft.ai.mcp.integration.case360;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.GregorianCalendar;
import java.util.Map;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;

import com.solusoft.ai.mcp.exception.Case360IntegrationException;
import com.solusoft.ai.mcp.integration.case360.soap.*;

import jakarta.xml.bind.JAXBElement;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class Case360SoapTemplate {

    private final WebServiceTemplate webServiceTemplate;
    private final ObjectFactory objectFactory = new ObjectFactory();
    private static final DatatypeFactory DATATYPE_FACTORY;

    static {
        try {
            DATATYPE_FACTORY = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException("Failed to init DatatypeFactory", e);
        }
    }

    public Case360SoapTemplate(@Qualifier("case360WebServiceTemplate") WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    public boolean executePing() {
        log.info("Entering ping");
        try {
            var request = new DoQueryByScriptName(); 
            request.setQueryScriptName("serverStatusCheck");
            webServiceTemplate.marshalSendAndReceive(objectFactory.createDoQueryByScriptName(request));
            return true;
        } catch (java.lang.Exception e) {
            String msg = e.getMessage().toLowerCase();
            if (msg.contains("refused") || msg.contains("timeout") || msg.contains("connect")) {
                log.error("Health Check Failed: Case360 is unreachable: {}", e.getMessage());
                return false;
            }
            return true;
        }
    }

    public String executeGetClaimStatus(String claimId) {
        log.info("Entering getClaimStatus");
        log.debug("Input claimId: {} ", claimId);
        if(claimId == null || claimId.isEmpty()) throw new Case360IntegrationException("claimId is null or empty");

        String queryScript = claimId.toUpperCase().startsWith("AUTO") ? "getMotorClaimByClaimId" : "getHCClaimByClaimId";
        var request = new DoQueryByScriptName();
        request.setQueryScriptName(queryScript);
        var param = new FieldPropertiesTO();
        param.setPropertyName("CLAIM_ID");
        param.setStringValue(claimId);
        param.setDataType(4); 
        var paramWrapper = new FieldPropertiesTOArray();
        paramWrapper.getFieldPropertiesTO().add(param);
        request.setQueryProperties(paramWrapper);

        @SuppressWarnings("unchecked")
        JAXBElement<DoQueryByScriptNameResponse> resp = (JAXBElement<DoQueryByScriptNameResponse>) webServiceTemplate.marshalSendAndReceive(objectFactory.createDoQueryByScriptName(request));
        
        String result = resp.getValue().getReturn().getFmsRowSetTO().getFirst().getFmsRowTO().getFirst().getFieldList().stream()
                .filter(field -> "CLAIM_STATUS".equals(field.getFieldName()))
                .findFirst().map(FmsFieldTO::getStringValue).orElse(null);
        
        log.debug("Return value (Status): {}", result);
        log.info("Exiting getClaimStatus successfully");
        return result;
    }

    public BigDecimal executeGetTemplateId(String templateName, String scriptName) {
        log.info("Entering executeGetTemplateId for script: {}", scriptName);
        var request = new DoQueryByScriptName();
        request.setQueryScriptName(scriptName);
        var param = new FieldPropertiesTO();
        param.setPropertyName("TEMPLATENAME");
        param.setStringValue(templateName);
        param.setDataType(4); 
        var paramWrapper = new FieldPropertiesTOArray();
        paramWrapper.getFieldPropertiesTO().add(param);
        request.setQueryProperties(paramWrapper);

        @SuppressWarnings("unchecked")
        JAXBElement<DoQueryByScriptNameResponse> resp = (JAXBElement<DoQueryByScriptNameResponse>) webServiceTemplate.marshalSendAndReceive(objectFactory.createDoQueryByScriptName(request));
        BigDecimal result = resp.getValue().getReturn().getFmsRowSetTO().getFirst().getFmsRowTO().getFirst().getFieldList().get(0).getBigDecimalValue().getValue();
        log.debug("Return value (Template ID): {}", result);
        return result;
    }

    public String executeCreateCase(BigDecimal templateId) {
        log.info("Entering executeCreateCase");
        var request = new CreateCaseFolder();
        request.setCaseFolderTemplateId(templateId);
        @SuppressWarnings("unchecked")
        JAXBElement<CreateCaseFolderResponse> resp = (JAXBElement<CreateCaseFolderResponse>) webServiceTemplate.marshalSendAndReceive(objectFactory.createCreateCaseFolder(request));
        String result = String.valueOf(resp.getValue().getReturn());
        log.debug("New Case ID: {}", result);
        log.info("Exiting executeCreateCase successfully");
        return result;
    }

    public void executeUpdateFields(String strCaseId, Map<String, Object> updates) {
        log.info("Entering executeUpdateFields for caseId: {}", strCaseId);
        BigDecimal caseId = new BigDecimal(strCaseId);
        var getReq = new GetCaseFolderFields();
        getReq.setCaseFolderId(caseId);
        @SuppressWarnings("unchecked")
        JAXBElement<GetCaseFolderFieldsResponse> getResp = (JAXBElement<GetCaseFolderFieldsResponse>) webServiceTemplate.marshalSendAndReceive(objectFactory.createGetCaseFolderFields(getReq));
        
        FmsRowTO fields = getResp.getValue().getReturn();
        FmsRowTO newFields = getResp.getValue().getReturn();
        ZoneId zoneId = ZoneId.systemDefault();

        for (FmsFieldTO field : newFields.getFieldList()) {
            if (updates.containsKey(field.getFieldName())) {
                Object val = updates.get(field.getFieldName());
                if (val != null) {
                    field.setModified(true);
                    field.setNullValue(false);
                    switch (field.getDataType()) {
                        case 4 -> field.setStringValue(String.valueOf(val));
                        case 5 -> { if (val instanceof LocalDate ld) field.setCalendarValue(DATATYPE_FACTORY.newXMLGregorianCalendar(GregorianCalendar.from(ld.atStartOfDay(zoneId)))); }
                        case 2 -> field.setIntValue(Integer.valueOf(val.toString()));
                        case 1 -> field.setBooleanValue(Boolean.valueOf(val.toString()));
                        case 6 -> field.setBigDecimalValue(objectFactory.createFmsFieldTOBigDecimalValue(new BigDecimal(val.toString())));
                        default -> field.setStringValue(val.toString());
                    }
                }
            } 
        }
        var setReq = new SetCaseFolderFields();
        setReq.setCaseFolderInstanceId(caseId);
        setReq.setOriginalCaseFolderFields(fields);
        setReq.setNewCaseFolderFields(newFields);
        setReq.setBForceUpdate(true); 
        webServiceTemplate.marshalSendAndReceive(objectFactory.createSetCaseFolderFields(setReq));
        log.info("Exiting executeUpdateFields successfully");
    }

    public String executeCreateFileStore(BigDecimal templateId) {
        log.info("Entering executeCreateFileStore");
        var request = new CreateFileStore();
        request.setTemplateId(templateId);
        @SuppressWarnings("unchecked")
        JAXBElement<CreateFileStoreResponse> resp = (JAXBElement<CreateFileStoreResponse>) webServiceTemplate.marshalSendAndReceive(objectFactory.createCreateFileStore(request));
        log.info("Exiting executeCreateFileStore successfully");
        return String.valueOf(resp.getValue().getReturn());
    }

    public void executeUploadDocument(BigDecimal docId, byte[] content, String fileName) {
        log.info("Entering executeUploadDocument: {}", fileName);
        var request = new PutFile();
        request.setData(content);
        request.setDocumentId(docId);
        request.setFileName(fileName);
        webServiceTemplate.marshalSendAndReceive(objectFactory.createPutFile(request));
        log.info("Exiting executeUploadDocument successfully");
    }

    public void executeRemoveCaseFolder(String strCaseId) {
        log.info("Entering executeRemoveCaseFolder");
        log.warn("EXECUTE ROLLBACK: Removing case {}", strCaseId);
        var req = new RemoveCaseFolder();
        req.setCaseFolderId(new BigDecimal(strCaseId));
        webServiceTemplate.marshalSendAndReceive(objectFactory.createRemoveCaseFolder(req));
        log.info("Exiting executeRemoveCaseFolder successfully");
    }

    public void executeRemoveFileStore(String strDocId) {
        log.info("Entering executeRemoveFileStore");
        log.warn("EXECUTE ROLLBACK: Removing document {}", strDocId);
        var req = new RemoveFileStore();
        req.setFileStoreId(new BigDecimal(strDocId));
        webServiceTemplate.marshalSendAndReceive(objectFactory.createRemoveFileStore(req));
        log.info("Exiting executeRemoveFileStore successfully");
    }
}