package com.solusoft.ai.mcp.integration.case360;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/test/case360")
public class Case360TestController {

    private final Case360Client case360Client;

    public Case360TestController(Case360Client case360Client) {
        this.case360Client = case360Client;
    }

    // URL: http://localhost:8080/test/case360/template?name=Motor Claim
    @GetMapping("/case/template")
    public String testGetTemplateId(@RequestParam String name) {
        try {
            BigDecimal id = case360Client.getCaseFolderTemplateId(name);
            return "SUCCESS: Template '" + name + "' has ID: " + id;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }
    
    @GetMapping("/file/template")
    public String testGetFileTemplateId(@RequestParam String name) {
        try {
            BigDecimal id = case360Client.getCaseFolderTemplateId(name);
            return "SUCCESS: Template '" + name + "' has ID: " + id;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    // URL: http://localhost:8080/test/case360/create?templateId=123
    @PostMapping("/case/create")
    public String testCreateCase(@RequestParam BigDecimal templateId) {
        try {
            String caseId = case360Client.createCase(templateId);
            return "SUCCESS: Created Case ID: " + caseId;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    // URL: http://localhost:8080/test/case360/update?caseId=555
    // Body (JSON): { "ClaimAmount": 500, "Description": "Test Update" }
    @PostMapping("/case/update")
    public String testUpdateCase(@RequestParam String caseId, @RequestBody Map<String, Object> updates) {
        try {
            case360Client.updateCaseFields(caseId, updates);
            return "SUCCESS: Updated fields for Case " + caseId;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }
    
    @PostMapping(value = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String testUpdateCase(@RequestParam String docId, @RequestBody MultipartFile file) {
        try {
            case360Client.uploadDocument(new BigDecimal(docId), file.getBytes(),"1.pdf");
            return "SUCCESS: Updated fields for docId " + docId;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }
}