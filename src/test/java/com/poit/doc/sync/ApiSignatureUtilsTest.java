package com.poit.doc.sync;

import com.ly.doc.model.ApiParam;
import com.poit.doc.sync.util.ApiSignatureUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ApiSignatureUtilsTest {

    @Test
    void same_structure_different_order_same_md5() {
        ApiParam a = mockParam("b", "int", false);
        ApiParam b = mockParam("a", "String", true);
        String md5Order1 = ApiSignatureUtils.generateParamsSignature(Arrays.asList(a, b));
        String md5Order2 = ApiSignatureUtils.generateParamsSignature(Arrays.asList(b, a));
        assertEquals(md5Order1, md5Order2);
    }

    @Test
    void different_field_changes_md5() {
        ApiParam x = mockParam("id", "long", true);
        String md5a = ApiSignatureUtils.generateParamsSignature(Collections.singletonList(x));
        ApiParam y = mockParam("id", "String", true);
        String md5b = ApiSignatureUtils.generateParamsSignature(Collections.singletonList(y));
        assertNotEquals(md5a, md5b);
    }

    @Test
    void empty_params_stable() {
        String e1 = ApiSignatureUtils.generateParamsSignature(null);
        String e2 = ApiSignatureUtils.generateParamsSignature(new ArrayList<>());
        assertEquals(e1, e2);
    }

    @Test
    void model_fields_md5_stable() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("name", "x");
        r1.put("type", "int");
        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("name", "y");
        r2.put("type", "String");
        rows.add(r2);
        rows.add(r1);
        String md5 = ApiSignatureUtils.md5ModelFieldsRows(rows);
        List<Map<String, Object>> rows2 = new ArrayList<>();
        rows2.add(r1);
        rows2.add(r2);
        assertEquals(md5, ApiSignatureUtils.md5ModelFieldsRows(rows2));
    }

    private static ApiParam mockParam(String field, String type, boolean required) {
        ApiParam p = Mockito.mock(ApiParam.class);
        Mockito.when(p.getField()).thenReturn(field);
        Mockito.when(p.getType()).thenReturn(type);
        Mockito.when(p.isRequired()).thenReturn(required);
        Mockito.when(p.getChildren()).thenReturn(null);
        return p;
    }
}
