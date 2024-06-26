/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.*;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.ConfigSubService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.YamlParserUtil;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.ServletContext;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class ConfigControllerTest {
    
    @InjectMocks
    ConfigController configController;
    
    private MockMvc mockmvc;
    
    @Mock
    private ServletContext servletContext;
    
    @Mock
    private ConfigServletInner inner;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    @Mock
    private NamespacePersistService namespacePersistService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private ConfigSubService configSubService;
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(configController, "configSubService", configSubService);
        ReflectionTestUtils.setField(configController, "configInfoPersistService", configInfoPersistService);
        ReflectionTestUtils.setField(configController, "configInfoBetaPersistService", configInfoBetaPersistService);
        ReflectionTestUtils.setField(configController, "namespacePersistService", namespacePersistService);
        ReflectionTestUtils.setField(configController, "configOperationService", configOperationService);
        ReflectionTestUtils.setField(configController, "inner", inner);
        mockmvc = MockMvcBuilders.standaloneSetup(configController).build();
    }
    
    @Test
    public void testPublishConfig() throws Exception {
        when(configOperationService.publishConfig(any(), any(), anyString())).thenReturn(true);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.CONFIG_CONTROLLER_PATH)
                .param("dataId", "test").param("group", "test").param("tenant", "").param("content", "test")
                .param("tag", "").param("appName", "").param("src_user", "").param("config_tags", "").param("desc", "")
                .param("use", "").param("effect", "").param("type", "").param("schema", "");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("true", actualValue);
    }
    
    @Test
    public void testGetConfig() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_PATH)
                .param("dataId", "test").param("group", "test").param("tenant", "").param("tag", "");
        
        int actualValue = mockmvc.perform(builder).andReturn().getResponse().getStatus();
        Assert.assertEquals(200, actualValue);
    }
    
    @Test
    public void testDetailConfigInfo() throws Exception {
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("test");
        configAllInfo.setGroup("test");
        configAllInfo.setCreateIp("localhost");
        configAllInfo.setCreateUser("test");
        
        when(configInfoPersistService.findConfigAllInfo("test", "test", "")).thenReturn(configAllInfo);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_PATH)
                .param("show", "all").param("dataId", "test").param("group", "test").param("tenant", "");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        ConfigAllInfo resConfigAllInfo = JacksonUtils.toObj(actualValue, ConfigAllInfo.class);
        
        Assert.assertEquals(configAllInfo.getDataId(), resConfigAllInfo.getDataId());
        Assert.assertEquals(configAllInfo.getGroup(), resConfigAllInfo.getGroup());
        Assert.assertEquals(configAllInfo.getCreateIp(), resConfigAllInfo.getCreateIp());
        Assert.assertEquals(configAllInfo.getCreateUser(), resConfigAllInfo.getCreateUser());
    }
    
    @Test
    public void testDeleteConfig() throws Exception {
        when(configOperationService.deleteConfig(anyString(), anyString(), anyString(), anyString(), any(),
                any())).thenReturn(true);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.CONFIG_CONTROLLER_PATH)
                .param("dataId", "test").param("group", "test").param("tenant", "").param("tag", "");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("true", actualValue);
    }
    
    @Test
    public void testDeleteConfigs() throws Exception {
        
        List<ConfigInfo> resultInfos = new ArrayList<>();
        String dataId = "dataId1123";
        String group = "group34567";
        String tenant = "tenant45678";
        resultInfos.add(new ConfigInfo(dataId, group, tenant));
        Mockito.when(configInfoPersistService.removeConfigInfoByIds(eq(Arrays.asList(1L, 2L)), anyString(), eq(null)))
                .thenReturn(resultInfos);
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.CONFIG_CONTROLLER_PATH)
                .param("delType", "ids").param("ids", "1,2");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        Assert.assertEquals("200", code);
        Assert.assertEquals("true", data);
        Thread.sleep(200L);
        //expect
        Assert.assertTrue(reference.get() != null);
    }
    
    @Test
    public void testGetConfigAdvanceInfo() throws Exception {
        
        ConfigAdvanceInfo configAdvanceInfo = new ConfigAdvanceInfo();
        configAdvanceInfo.setCreateIp("localhost");
        configAdvanceInfo.setCreateUser("test");
        configAdvanceInfo.setDesc("desc");
        
        when(configInfoPersistService.findConfigAdvanceInfo("test", "test", "")).thenReturn(configAdvanceInfo);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(
                        Constants.CONFIG_CONTROLLER_PATH + "/catalog").param("dataId", "test").param("group", "test")
                .param("tenant", "");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        ConfigAdvanceInfo resConfigAdvanceInfo = JacksonUtils.toObj(data, ConfigAdvanceInfo.class);
        
        Assert.assertEquals("200", code);
        Assert.assertEquals(configAdvanceInfo.getCreateIp(), resConfigAdvanceInfo.getCreateIp());
        Assert.assertEquals(configAdvanceInfo.getCreateUser(), resConfigAdvanceInfo.getCreateUser());
        Assert.assertEquals(configAdvanceInfo.getDesc(), resConfigAdvanceInfo.getDesc());
    }
    
    @Test
    public void testListener() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(
                Constants.CONFIG_CONTROLLER_PATH + "/listener").param("Listening-Configs", "test");
        int actualValue = mockmvc.perform(builder).andReturn().getResponse().getStatus();
        Assert.assertEquals(200, actualValue);
    }
    
    @Test
    public void testGetListeners() throws Exception {
        Map<String, String> listenersGroupkeyStatus = new HashMap<>();
        listenersGroupkeyStatus.put("test", "test");
        SampleResult sampleResult = new SampleResult();
        sampleResult.setLisentersGroupkeyStatus(listenersGroupkeyStatus);
        
        when(configSubService.getCollectSampleResult("test", "test", "", 1)).thenReturn(sampleResult);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(
                        Constants.CONFIG_CONTROLLER_PATH + "/listener").param("dataId", "test").param("group", "test")
                .param("tenant", "").param("sampleTime", "1");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        GroupkeyListenserStatus groupkeyListenserStatus = JacksonUtils.toObj(actualValue,
                GroupkeyListenserStatus.class);
        Assert.assertEquals(200, groupkeyListenserStatus.getCollectStatus());
        Assert.assertEquals(1, groupkeyListenserStatus.getLisentersGroupkeyStatus().size());
        Assert.assertEquals("test", groupkeyListenserStatus.getLisentersGroupkeyStatus().get("test"));
    }
    
    @Test
    public void testSearchConfig() throws Exception {
        List<ConfigInfo> configInfoList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo("test", "test", "test");
        configInfoList.add(configInfo);
        
        Page<ConfigInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configInfoList);
        Map<String, Object> configAdvanceInfo = new HashMap<>(8);
        
        when(configInfoPersistService.findConfigInfo4Page(1, 10, "test", "test", "", configAdvanceInfo)).thenReturn(
                page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_PATH)
                .param("search", "accurate").param("dataId", "test").param("group", "test").param("appName", "")
                .param("tenant", "").param("config_tags", "").param("pageNo", "1").param("pageSize", "10");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        JsonNode pageItemsNode = JacksonUtils.toObj(actualValue).get("pageItems");
        List resultList = JacksonUtils.toObj(pageItemsNode.toString(), List.class);
        ConfigInfo resConfigInfo = JacksonUtils.toObj(pageItemsNode.get(0).toString(), ConfigInfo.class);
        
        Assert.assertEquals(configInfoList.size(), resultList.size());
        Assert.assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        Assert.assertEquals(configInfo.getGroup(), resConfigInfo.getGroup());
        Assert.assertEquals(configInfo.getContent(), resConfigInfo.getContent());
    }
    
    @Test
    public void testFuzzySearchConfig() throws Exception {
        
        List<ConfigInfo> configInfoList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo("test", "test", "test");
        configInfoList.add(configInfo);
        
        Page<ConfigInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configInfoList);
        Map<String, Object> configAdvanceInfo = new HashMap<>(8);
        
        when(configInfoPersistService.findConfigInfoLike4Page(1, 10, "test", "test", "", configAdvanceInfo)).thenReturn(
                page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_PATH)
                .param("search", "blur").param("dataId", "test").param("group", "test").param("appName", "")
                .param("tenant", "").param("config_tags", "").param("pageNo", "1").param("pageSize", "10");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        List resultList = JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get("pageItems").toString(), List.class);
        ConfigInfo resConfigInfo = JacksonUtils.toObj(
                JacksonUtils.toObj(actualValue).get("pageItems").get(0).toString(), ConfigInfo.class);
        
        Assert.assertEquals(configInfoList.size(), resultList.size());
        Assert.assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        Assert.assertEquals(configInfo.getGroup(), resConfigInfo.getGroup());
        Assert.assertEquals(configInfo.getContent(), resConfigInfo.getContent());
    }
    
    @Test
    public void testStopBeta() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.CONFIG_CONTROLLER_PATH)
                .param("beta", "true").param("dataId", "test").param("group", "test").param("tenant", "");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        Assert.assertEquals("200", code);
        Assert.assertEquals("true", data);
    }
    
    @Test
    public void testQueryBeta() throws Exception {
        
        ConfigInfoBetaWrapper configInfoBetaWrapper = new ConfigInfoBetaWrapper();
        configInfoBetaWrapper.setDataId("test");
        configInfoBetaWrapper.setGroup("test");
        configInfoBetaWrapper.setContent("test");
        
        when(configInfoBetaPersistService.findConfigInfo4Beta("test", "test", "")).thenReturn(configInfoBetaWrapper);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_PATH)
                .param("beta", "true").param("dataId", "test").param("group", "test").param("tenant", "");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        ConfigInfoBetaWrapper resConfigInfoBetaWrapper = JacksonUtils.toObj(data, ConfigInfoBetaWrapper.class);
        
        Assert.assertEquals("200", code);
        Assert.assertEquals(configInfoBetaWrapper.getDataId(), resConfigInfoBetaWrapper.getDataId());
        Assert.assertEquals(configInfoBetaWrapper.getGroup(), resConfigInfoBetaWrapper.getGroup());
        Assert.assertEquals(configInfoBetaWrapper.getContent(), resConfigInfoBetaWrapper.getContent());
    }
    
    @Test
    public void testExportConfig() throws Exception {
        
        String dataId = "dataId1.json";
        String group = "group2";
        String tenant = "tenant234";
        String appname = "appname2";
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(group);
        configAllInfo.setTenant(tenant);
        configAllInfo.setContent("contet45678");
        configAllInfo.setAppName(appname);
        List<ConfigAllInfo> dataList = new ArrayList<>();
        dataList.add(configAllInfo);
        
        Mockito.when(configInfoPersistService.findAllConfigInfo4Export(eq(dataId), eq(group), eq(tenant), eq(appname),
                eq(Arrays.asList(1L, 2L)))).thenReturn(dataList);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_PATH)
                .param("export", "true").param("dataId", dataId).param("group", group).param("tenant", tenant)
                .param("appName", appname).param("ids", "1,2");
        
        int actualValue = mockmvc.perform(builder).andReturn().getResponse().getStatus();
        
        Assert.assertEquals(200, actualValue);
    }
    
    @Test
    public void testExportConfigV2() throws Exception {
        String dataId = "dataId2.json";
        String group = "group2";
        String tenant = "tenant234";
        String appname = "appname2";
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(group);
        configAllInfo.setTenant(tenant);
        configAllInfo.setAppName(appname);
        configAllInfo.setContent("content1234");
        List<ConfigAllInfo> dataList = new ArrayList<>();
        dataList.add(configAllInfo);
        Mockito.when(configInfoPersistService.findAllConfigInfo4Export(eq(dataId), eq(group), eq(tenant), eq(appname),
                eq(Arrays.asList(1L, 2L)))).thenReturn(dataList);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_PATH)
                .param("exportV2", "true").param("dataId", dataId).param("group", group).param("tenant", tenant)
                .param("appName", appname).param("ids", "1,2");
        
        int actualValue = mockmvc.perform(builder).andReturn().getResponse().getStatus();
        
        Assert.assertEquals(200, actualValue);
    }
    
    @Test
    public void testImportAndPublishConfig() throws Exception {
        MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class);
        List<ZipUtils.ZipItem> zipItems = new ArrayList<>();
        ZipUtils.ZipItem zipItem = new ZipUtils.ZipItem("test/test", "test");
        zipItems.add(zipItem);
        ZipUtils.UnZipResult unziped = new ZipUtils.UnZipResult(zipItems, null);
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        
        zipUtilsMockedStatic.when(() -> ZipUtils.unzip(file.getBytes())).thenReturn(unziped);
        when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        Map<String, Object> map = new HashMap<>();
        map.put("test", "test");
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(), any(),
                any())).thenReturn(map);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(Constants.CONFIG_CONTROLLER_PATH)
                .file(file).param("import", "true").param("src_user", "test").param("namespace", "public")
                .param("policy", "ABORT");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        Assert.assertEquals("200", code);
        Map<String, Object> resultMap = JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get("data").toString(),
                Map.class);
        Assert.assertEquals(map.get("test"), resultMap.get("test").toString());
        
        zipUtilsMockedStatic.close();
    }
    
    @Test
    public void testImportAndPublishConfigV2() throws Exception {
        List<ZipUtils.ZipItem> zipItems = new ArrayList<>();
        String dataId = "dataId23456.json";
        String group = "group132";
        String content = "content1234";
        ZipUtils.ZipItem zipItem = new ZipUtils.ZipItem(group + "/" + dataId, content);
        zipItems.add(zipItem);
        ConfigMetadata configMetadata = new ConfigMetadata();
        configMetadata.setMetadata(new ArrayList<>());
        ConfigMetadata.ConfigExportItem configExportItem = new ConfigMetadata.ConfigExportItem();
        configExportItem.setDataId(dataId);
        configExportItem.setGroup(group);
        configExportItem.setType("json");
        configExportItem.setAppName("appna123");
        configMetadata.getMetadata().add(configExportItem);
        ZipUtils.UnZipResult unziped = new ZipUtils.UnZipResult(zipItems,
                new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW, YamlParserUtil.dumpObject(configMetadata)));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class);
        zipUtilsMockedStatic.when(() -> ZipUtils.unzip(eq(file.getBytes()))).thenReturn(unziped);
        when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        Map<String, Object> map = new HashMap<>();
        map.put("test", "test");
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(), any(),
                any())).thenReturn(map);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(Constants.CONFIG_CONTROLLER_PATH)
                .file(file).param("import", "true").param("src_user", "test").param("namespace", "public")
                .param("policy", "ABORT");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        Assert.assertEquals("200", code);
        Map<String, Object> resultMap = JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get("data").toString(),
                Map.class);
        Assert.assertEquals(map.get("test"), resultMap.get("test").toString());
        
        zipUtilsMockedStatic.close();
    }
    
    @Test
    public void testCloneConfig() throws Exception {
        SameNamespaceCloneConfigBean sameNamespaceCloneConfigBean = new SameNamespaceCloneConfigBean();
        sameNamespaceCloneConfigBean.setCfgId(1L);
        sameNamespaceCloneConfigBean.setDataId("test");
        sameNamespaceCloneConfigBean.setGroup("test");
        List<SameNamespaceCloneConfigBean> configBeansList = new ArrayList<>();
        configBeansList.add(sameNamespaceCloneConfigBean);
        
        when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("test");
        configAllInfo.setGroup("test");
        List<ConfigAllInfo> queryedDataList = new ArrayList<>();
        queryedDataList.add(configAllInfo);
        
        List<Long> idList = new ArrayList<>(configBeansList.size());
        idList.add(sameNamespaceCloneConfigBean.getCfgId());
        
        when(configInfoPersistService.findAllConfigInfo4Export(null, null, null, null, idList)).thenReturn(
                queryedDataList);
        
        Map<String, Object> map = new HashMap<>();
        map.put("test", "test");
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(), any(),
                any())).thenReturn(map);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.CONFIG_CONTROLLER_PATH)
                .param("clone", "true").param("src_user", "test").param("tenant", "public").param("policy", "ABORT")
                .content(JacksonUtils.toJson(configBeansList)).contentType(MediaType.APPLICATION_JSON);
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        Assert.assertEquals("200", code);
        Map<String, Object> resultMap = JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get("data").toString(),
                Map.class);
        Assert.assertEquals(map.get("test"), resultMap.get("test").toString());
    }
}
