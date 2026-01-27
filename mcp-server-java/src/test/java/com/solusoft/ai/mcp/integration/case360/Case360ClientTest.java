package com.solusoft.ai.mcp.integration.case360;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import javax.xml.namespace.QName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
import com.solusoft.ai.mcp.integration.case360.soap.FmsRowSetTO;
import com.solusoft.ai.mcp.integration.case360.soap.FmsRowSetTOArray;
import com.solusoft.ai.mcp.integration.case360.soap.FmsRowTO;
import com.solusoft.ai.mcp.integration.case360.soap.GetCaseFolderFieldsResponse;
import com.solusoft.ai.mcp.integration.case360.soap.PutFile;
import com.solusoft.ai.mcp.integration.case360.soap.PutFileResponse;

import jakarta.xml.bind.JAXBElement;

public class Case360ClientTest {

    @Mock
    private WebServiceTemplate webServiceTemplate;
    

    private Case360Client client;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        client = new Case360Client(webServiceTemplate);
    }

    @Test
    public void testGetCaseFolderTemplateId_parsesNestedResponse() {
        // Build nested response: DoQueryByScriptNameResponse -> FmsRowSetTOArray -> FmsRowSetTO -> FmsRowTO -> FmsFieldTO
        DoQueryByScriptNameResponse resp = new DoQueryByScriptNameResponse();
        FmsRowSetTOArray arr = new FmsRowSetTOArray();
        FmsRowSetTO set = new FmsRowSetTO();
        FmsRowTO row = new FmsRowTO();

        FmsFieldTO field = new FmsFieldTO();
        // field.bigDecimalValue is JAXBElement<BigDecimal>, set via setter using new JAXBElement
        var big = new BigDecimal("42");
        JAXBElement<BigDecimal> bigEl = new JAXBElement<>(new QName("","bigDecimalValue"), BigDecimal.class, big);
        field.setBigDecimalValue(bigEl);

        field.setStringValue("ignored");
        field.setFieldName("TEMPLATENAME");

        row.getFieldList().add(field);
        set.getFmsRowTO().add(row);
        arr.getFmsRowSetTO().add(set);
        resp.setReturn(arr);

        JAXBElement<DoQueryByScriptNameResponse> wrapper = new JAXBElement<>(new QName("","doQueryByScriptNameResponse"), DoQueryByScriptNameResponse.class, resp);

        when(webServiceTemplate.marshalSendAndReceive(any())).thenReturn(wrapper);

        BigDecimal result = client.getCaseFolderTemplateId("Motor Claim");
        assertEquals(new BigDecimal("42"), result);
    }

    @Test
    public void testGetFilestoreTemplateId_parsesNestedResponse() {
        // Same structure as above with different number
        DoQueryByScriptNameResponse resp = new DoQueryByScriptNameResponse();
        FmsRowSetTOArray arr = new FmsRowSetTOArray();
        FmsRowSetTO set = new FmsRowSetTO();
        FmsRowTO row = new FmsRowTO();

        FmsFieldTO field = new FmsFieldTO();
        var big = new BigDecimal("123");
        JAXBElement<BigDecimal> bigEl = new JAXBElement<>(new QName("","bigDecimalValue"), BigDecimal.class, big);
        field.setBigDecimalValue(bigEl);
        row.getFieldList().add(field);
        set.getFmsRowTO().add(row);
        arr.getFmsRowSetTO().add(set);
        resp.setReturn(arr);

        JAXBElement<DoQueryByScriptNameResponse> wrapper = new JAXBElement<>(new QName("","doQueryByScriptNameResponse"), DoQueryByScriptNameResponse.class, resp);

        when(webServiceTemplate.marshalSendAndReceive(any())).thenReturn(wrapper);

        BigDecimal result = client.getFilestoreTemplateId("Claim Document");
        assertEquals(new BigDecimal("123"), result);
    }

    @Test
    public void testCreateCase_andCreateFileStore_returnsStringIds() {
        CreateCaseFolderResponse caseResp = new CreateCaseFolderResponse();
        caseResp.setReturn(new BigDecimal("999"));
        JAXBElement<CreateCaseFolderResponse> caseWrap = new JAXBElement<>(new QName("","createCaseFolderResponse"), CreateCaseFolderResponse.class, caseResp);

        CreateFileStoreResponse fsResp = new CreateFileStoreResponse();
        fsResp.setReturn(new BigDecimal("555"));
        JAXBElement<CreateFileStoreResponse> fsWrap = new JAXBElement<>(new QName("","createFileStoreResponse"), CreateFileStoreResponse.class, fsResp);

        // The client may call marshalSendAndReceive multiple times; sequence them
        when(webServiceTemplate.marshalSendAndReceive(any())).thenReturn(caseWrap).thenReturn(fsWrap);

        String caseId = client.createCase(new BigDecimal("1"));
        assertTrue(caseId.contains("999"));

        String fsId = client.createFileStore(new BigDecimal("2"));
        assertTrue(fsId.contains("555"));
    }

    @Test
    public void testUpdateCaseFields_modifiesAndCallsSet() {
        // Build GetCaseFolderFieldsResponse returning FmsRowTO with one FmsFieldTO named "CLAIMANT_NAME"
        GetCaseFolderFieldsResponse getResp = new GetCaseFolderFieldsResponse();
        FmsRowTO row = new FmsRowTO();
        FmsFieldTO field = new FmsFieldTO();
        field.setFieldName("CLAIMANT_NAME");
        field.setStringValue("Old Name");
        row.getFieldList().add(field);
        getResp.setReturn(row);

        JAXBElement<GetCaseFolderFieldsResponse> getWrap = new JAXBElement<>(new QName("","getCaseFolderFieldsResponse"), GetCaseFolderFieldsResponse.class, getResp);

        // Prepare a real SetCaseFolderFieldsResponse instance for the second call
        com.solusoft.ai.mcp.integration.case360.soap.SetCaseFolderFieldsResponse setResp = new com.solusoft.ai.mcp.integration.case360.soap.SetCaseFolderFieldsResponse();
        JAXBElement<com.solusoft.ai.mcp.integration.case360.soap.SetCaseFolderFieldsResponse> setWrap = new JAXBElement<>(new QName("","setCaseFolderFieldsResponse"), com.solusoft.ai.mcp.integration.case360.soap.SetCaseFolderFieldsResponse.class, setResp);

        // First call returns getWrap, second call returns setWrap
        when(webServiceTemplate.marshalSendAndReceive(any())).thenReturn(getWrap).thenReturn(setWrap);

        client.updateCaseFields("123", java.util.Map.of("CLAIMANT_NAME", "New Name"));

        // verify that marshalSendAndReceive was called at least twice (get + set)
        verify(webServiceTemplate, atLeast(2)).marshalSendAndReceive(any());

        // ensure the field was modified in the Get response object before set (the client's code modifies the same object and passes it to set)
        assertEquals("New Name", row.getFieldList().get(0).getStringValue());
        assertTrue(row.getFieldList().get(0).isModified());
    }

    @Test
    public void testUploadDocument_callsPutFile() {
        PutFileResponse putResp = new PutFileResponse();
        JAXBElement<PutFileResponse> putWrap = new JAXBElement<>(new QName("","putFileResponse"), PutFileResponse.class, putResp);

        when(webServiceTemplate.marshalSendAndReceive(any())).thenReturn(putWrap);

        byte[] content = new byte[] {1,2,3};
        client.uploadDocument(new BigDecimal("777"), content, "file.bin");

        verify(webServiceTemplate, times(1)).marshalSendAndReceive(any());
    }

    @Test
    public void testDoQuery_requestShaping() {
        // Prepare response wrapper so call succeeds
        DoQueryByScriptNameResponse resp = new DoQueryByScriptNameResponse();
        FmsRowSetTOArray arr = new FmsRowSetTOArray();
        FmsRowSetTO set = new FmsRowSetTO();
        FmsRowTO row = new FmsRowTO();
        FmsFieldTO field = new FmsFieldTO();
        jakarta.xml.bind.JAXBElement<BigDecimal> bigEl = new jakarta.xml.bind.JAXBElement<>(new QName("","bigDecimalValue"), BigDecimal.class, new BigDecimal("7"));
        field.setBigDecimalValue(bigEl);
        row.getFieldList().add(field);
        set.getFmsRowTO().add(row);
        arr.getFmsRowSetTO().add(set);
        resp.setReturn(arr);
        JAXBElement<DoQueryByScriptNameResponse> wrapper = new JAXBElement<>(new QName("","doQueryByScriptNameResponse"), DoQueryByScriptNameResponse.class, resp);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        when(webServiceTemplate.marshalSendAndReceive(captor.capture())).thenReturn(wrapper);

        BigDecimal result = client.getCaseFolderTemplateId("MyTemplate");
        assertEquals(new BigDecimal("7"), result);

        Object sent = captor.getValue();
        assertTrue(sent instanceof JAXBElement);
        JAXBElement<?> je = (JAXBElement<?>) sent;
        assertTrue(je.getValue() instanceof DoQueryByScriptName);
        DoQueryByScriptName dq = (DoQueryByScriptName) je.getValue();
        assertEquals("getCaseTemplateIdFromName", dq.getQueryScriptName());
        FieldPropertiesTOArray props = dq.getQueryProperties();
        assertNotNull(props);
        FieldPropertiesTO p = props.getFieldPropertiesTO().get(0);
        assertEquals("TEMPLATENAME", p.getPropertyName());
        assertEquals("MyTemplate", p.getStringValue());
    }

    @Test
    public void testCreateCase_requestShaping() {
        CreateCaseFolderResponse caseResp = new CreateCaseFolderResponse();
        caseResp.setReturn(new BigDecimal("999"));
        JAXBElement<CreateCaseFolderResponse> caseWrap = new JAXBElement<>(new QName("","createCaseFolderResponse"), CreateCaseFolderResponse.class, caseResp);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        when(webServiceTemplate.marshalSendAndReceive(captor.capture())).thenReturn(caseWrap);

        BigDecimal templateId = new BigDecimal("42");
        String caseId = client.createCase(templateId);
        assertTrue(caseId.contains("999"));

        Object sent = captor.getValue();
        assertTrue(sent instanceof JAXBElement);
        JAXBElement<?> je = (JAXBElement<?>) sent;
        assertTrue(je.getValue() instanceof CreateCaseFolder);
        CreateCaseFolder req = (CreateCaseFolder) je.getValue();
        assertEquals(templateId, req.getCaseFolderTemplateId());
    }

    @Test
    public void testCreateFileStore_requestShaping() {
        CreateFileStoreResponse fsResp = new CreateFileStoreResponse();
        fsResp.setReturn(new BigDecimal("555"));
        JAXBElement<CreateFileStoreResponse> fsWrap = new JAXBElement<>(new QName("","createFileStoreResponse"), CreateFileStoreResponse.class, fsResp);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        when(webServiceTemplate.marshalSendAndReceive(captor.capture())).thenReturn(fsWrap);

        BigDecimal templateId = new BigDecimal("77");
        String result = client.createFileStore(templateId);
        assertTrue(result.contains("555"));

        Object sent = captor.getValue();
        assertTrue(sent instanceof JAXBElement);
        JAXBElement<?> je = (JAXBElement<?>) sent;
        assertTrue(je.getValue() instanceof CreateFileStore);
        CreateFileStore req = (CreateFileStore) je.getValue();
        assertEquals(templateId, req.getTemplateId());
    }

    @Test
    public void testPutFile_requestShaping() {
        PutFileResponse putResp = new PutFileResponse();
        JAXBElement<PutFileResponse> putWrap = new JAXBElement<>(new QName("","putFileResponse"), PutFileResponse.class, putResp);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        when(webServiceTemplate.marshalSendAndReceive(captor.capture())).thenReturn(putWrap);

        BigDecimal docId = new BigDecimal("777");
        byte[] content = new byte[] {9,8,7};
        String fileName = "payload.bin";

        client.uploadDocument(docId, content, fileName);

        Object sent = captor.getValue();
        assertTrue(sent instanceof JAXBElement);
        JAXBElement<?> je = (JAXBElement<?>) sent;
        assertTrue(je.getValue() instanceof PutFile);
        PutFile req = (PutFile) je.getValue();
        assertEquals(docId, req.getDocumentId());
        assertArrayEquals(content, req.getData());
        assertEquals(fileName, req.getFileName());
    }
}
