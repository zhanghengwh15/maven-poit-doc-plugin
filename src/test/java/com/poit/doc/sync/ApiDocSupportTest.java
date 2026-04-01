package com.poit.doc.sync;

import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.ApiMethodDoc;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiDocSupportTest {

    @Test
    void should_return_empty_list_when_roots_is_null() {


        SmartDocRunConfig cfg = new SmartDocRunConfig();
        cfg.setBaseDir("/Users/zhangheng/poi_tech/poit-wine-mes/poit-wine-mes-app");
        List<ApiDoc> roots = SmartDocBootstrap.loadApiDocs(cfg);
        List<ApiDoc> result = ApiDocSupport.flattenControllerDocs(roots);
        assertTrue(result.isEmpty());
    }

    @Test
    void should_return_empty_list_when_roots_is_empty() {
        List<ApiDoc> result = ApiDocSupport.flattenControllerDocs(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void should_collect_root_api_doc_with_methods() {
        ApiMethodDoc methodDoc = Mockito.mock(ApiMethodDoc.class);
        ApiDoc rootDoc = Mockito.mock(ApiDoc.class);
        Mockito.when(rootDoc.getList()).thenReturn(Collections.singletonList(methodDoc));
        Mockito.when(rootDoc.getChildrenApiDocs()).thenReturn(null);

        List<ApiDoc> result = ApiDocSupport.flattenControllerDocs(Collections.singletonList(rootDoc));

        assertEquals(1, result.size());
        assertEquals(rootDoc, result.get(0));
    }

    @Test
    void should_skip_api_doc_without_methods() {
        ApiDoc rootDoc = Mockito.mock(ApiDoc.class);
        Mockito.when(rootDoc.getList()).thenReturn(null);
        Mockito.when(rootDoc.getChildrenApiDocs()).thenReturn(null);

        List<ApiDoc> result = ApiDocSupport.flattenControllerDocs(Collections.singletonList(rootDoc));

        assertTrue(result.isEmpty());
    }

    @Test
    void should_collect_children_api_docs_with_methods() {
        ApiMethodDoc childMethod = Mockito.mock(ApiMethodDoc.class);
        ApiDoc childDoc = Mockito.mock(ApiDoc.class);
        Mockito.when(childDoc.getList()).thenReturn(Collections.singletonList(childMethod));
        Mockito.when(childDoc.getChildrenApiDocs()).thenReturn(null);

        ApiDoc rootDoc = Mockito.mock(ApiDoc.class);
        Mockito.when(rootDoc.getList()).thenReturn(null);
        Mockito.when(rootDoc.getChildrenApiDocs()).thenReturn(Collections.singletonList(childDoc));

        List<ApiDoc> result = ApiDocSupport.flattenControllerDocs(Collections.singletonList(rootDoc));

        assertEquals(1, result.size());
        assertEquals(childDoc, result.get(0));
    }

    @Test
    void should_collect_both_root_and_children_with_methods() {
        ApiMethodDoc rootMethod = Mockito.mock(ApiMethodDoc.class);
        ApiMethodDoc childMethod = Mockito.mock(ApiMethodDoc.class);
        ApiMethodDoc grandChildMethod = Mockito.mock(ApiMethodDoc.class);

        ApiDoc grandChildDoc = Mockito.mock(ApiDoc.class);
        Mockito.when(grandChildDoc.getList()).thenReturn(Collections.singletonList(grandChildMethod));
        Mockito.when(grandChildDoc.getChildrenApiDocs()).thenReturn(null);

        ApiDoc childDoc = Mockito.mock(ApiDoc.class);
        Mockito.when(childDoc.getList()).thenReturn(Collections.singletonList(childMethod));
        Mockito.when(childDoc.getChildrenApiDocs()).thenReturn(Collections.singletonList(grandChildDoc));

        ApiDoc rootDoc = Mockito.mock(ApiDoc.class);
        Mockito.when(rootDoc.getList()).thenReturn(Collections.singletonList(rootMethod));
        Mockito.when(rootDoc.getChildrenApiDocs()).thenReturn(Collections.singletonList(childDoc));

        List<ApiDoc> result = ApiDocSupport.flattenControllerDocs(Collections.singletonList(rootDoc));

        assertEquals(3, result.size());
        assertTrue(result.contains(rootDoc));
        assertTrue(result.contains(childDoc));
        assertTrue(result.contains(grandChildDoc));
    }

    @Test
    void should_skip_null_doc_in_children() {
        ApiMethodDoc rootMethod = Mockito.mock(ApiMethodDoc.class);
        ApiDoc rootDoc = Mockito.mock(ApiDoc.class);
        Mockito.when(rootDoc.getList()).thenReturn(Collections.singletonList(rootMethod));

        List<ApiDoc> children = new ArrayList<>();
        children.add(null);
        Mockito.when(rootDoc.getChildrenApiDocs()).thenReturn(children);

        List<ApiDoc> result = ApiDocSupport.flattenControllerDocs(Collections.singletonList(rootDoc));

        assertEquals(1, result.size());
        assertEquals(rootDoc, result.get(0));
    }
}
