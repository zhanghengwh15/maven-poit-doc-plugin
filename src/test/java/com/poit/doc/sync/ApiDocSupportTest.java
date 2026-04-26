package com.poit.doc.sync;

import com.poit.doc.scanner.model.ApiField;
import com.poit.doc.scanner.model.ApiMethod;
import com.poit.doc.scanner.model.ApiParam;
import com.poit.doc.sync.entity.ModelInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiDocSupportTest {

    @Test
    void should_return_empty_models_when_no_body_or_response() {
        ApiMethod method = new ApiMethod();
        method.setParameters(List.of());

        Map<String, ModelInfo> result = ApiDocSupport.extractModelsFromRequestAndResponse(method);
        assertTrue(result.isEmpty());
    }

    @Test
    void should_extract_model_from_request_body() {
        ApiMethod method = new ApiMethod();
        ApiField bodySchema = buildSimpleObject("com.example.UserDTO", "UserDTO",
                List.of(
                        newField("id", "long", "User ID"),
                        newField("name", "string", "User name")
                ));
        ApiParam bodyParam = new ApiParam();
        bodyParam.setParamIn("body");
        bodyParam.setBodySchema(bodySchema);
        method.setParameters(List.of(bodyParam));

        Map<String, ModelInfo> result = ApiDocSupport.extractModelsFromRequestAndResponse(method);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("com.example.UserDTO"));
        ModelInfo info = result.get("com.example.UserDTO");
        assertEquals("UserDTO", info.getSimpleName());
    }

    @Test
    void should_extract_model_from_response_body() {
        ApiMethod method = new ApiMethod();
        ApiField response = buildSimpleObject("com.example.ResultVO", "ResultVO",
                List.of(
                        newField("code", "int", "Status code"),
                        newField("data", "object", "Data payload")
                ));
        method.setResponseBody(response);

        Map<String, ModelInfo> result = ApiDocSupport.extractModelsFromRequestAndResponse(method);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("com.example.ResultVO"));
    }

    @Test
    void should_resolve_req_model_ref_from_body_param() {
        ApiMethod method = new ApiMethod();
        ApiField bodySchema = buildSimpleObject("com.example.ReqDTO", "ReqDTO", List.of());
        ApiParam bodyParam = new ApiParam();
        bodyParam.setParamIn("body");
        bodyParam.setBodySchema(bodySchema);
        method.setParameters(List.of(bodyParam));

        String ref = ApiDocSupport.resolveReqModelRef(method);
        assertEquals("com.example.ReqDTO", ref);
    }

    @Test
    void should_resolve_res_model_ref_from_response_body() {
        ApiMethod method = new ApiMethod();
        ApiField response = buildSimpleObject("com.example.ResDTO", "ResDTO", List.of());
        method.setResponseBody(response);

        String ref = ApiDocSupport.resolveResModelRef(method);
        assertEquals("com.example.ResDTO", ref);
    }

    @Test
    void should_return_null_when_no_models() {
        ApiMethod method = new ApiMethod();
        assertNull(ApiDocSupport.resolveReqModelRef(method));
        assertNull(ApiDocSupport.resolveResModelRef(method));
    }

    @Test
    void should_handle_circular_reference_without_stack_overflow() {
        // A -> B -> A
        ApiField fieldA = new ApiField();
        fieldA.setName("A");
        fieldA.setType("object");
        fieldA.setRef("com.example.A");

        ApiField fieldB = new ApiField();
        fieldB.setName("b");
        fieldB.setType("object");
        fieldB.setRef("com.example.B");
        fieldB.setChildren(List.of());

        fieldA.setChildren(List.of(fieldB));

        // B references A back (simulated via nested child pointing to A's ref)
        ApiField fieldBackToA = new ApiField();
        fieldBackToA.setName("a");
        fieldBackToA.setType("object");
        fieldBackToA.setRef("com.example.A");
        fieldBackToA.setChildren(List.of());

        // Simulate: method response is A, which contains B, which contains A again
        // We build it as a tree where B has a child pointing to A
        fieldB.setChildren(List.of(fieldBackToA));

        ApiMethod method = new ApiMethod();
        method.setResponseBody(fieldA);

        // Should not throw StackOverflowError
        Map<String, ModelInfo> result = ApiDocSupport.extractModelsFromRequestAndResponse(method);
        // Should extract at least A
        assertTrue(result.containsKey("com.example.A"));
    }

    private static ApiField buildSimpleObject(String ref, String name, List<ApiField> children) {
        ApiField field = new ApiField();
        field.setName(name);
        field.setType("object");
        field.setRef(ref);
        field.setDescription(name + " description");
        field.setChildren(children);
        return field;
    }

    private static ApiField newField(String name, String type, String desc) {
        ApiField field = new ApiField();
        field.setName(name);
        field.setType(type);
        field.setDescription(desc);
        field.setRequired(false);
        return field;
    }
}
