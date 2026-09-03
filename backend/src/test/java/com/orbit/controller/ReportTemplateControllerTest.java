package com.orbit.controller;

import com.orbit.domain.config.ReportTemplate;
import com.orbit.repository.ReportTemplateRepository;
import com.orbit.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Export templates: default fetch + section persistence. */
@WebMvcTest(
    value = ReportTemplateController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class ReportTemplateControllerTest {

    @Autowired MockMvc mvc;
    @MockBean JwtService jwtService;
    @MockBean ReportTemplateRepository templates;

    private ReportTemplate tpl() {
        ReportTemplate t = new ReportTemplate();
        ReflectionTestUtils.setField(t, "id", 1L);
        t.setName("Account Delivery Report");
        t.setScope("acct");
        t.setSections("[{\"key\":\"keyMetrics\",\"enabled\":true}]");
        t.setDefaultTemplate(true);
        return t;
    }

    @Test
    void defaultTemplateForScopeIsServed() throws Exception {
        when(templates.findFirstByScopeAndDefaultTemplateTrue("acct")).thenReturn(Optional.of(tpl()));

        mvc.perform(get("/api/v1/report-templates/default"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Account Delivery Report"))
            .andExpect(jsonPath("$.defaultTemplate").value(true));
    }

    @Test
    void missingDefaultReturns404() throws Exception {
        when(templates.findFirstByScopeAndDefaultTemplateTrue("pod")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/report-templates/default").param("scope", "pod"))
            .andExpect(status().isNotFound());
    }

    @Test
    void putPersistsReorderedSections() throws Exception {
        ReportTemplate t = tpl();
        when(templates.findById(1L)).thenReturn(Optional.of(t));
        when(templates.save(any(ReportTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        String sections = "[{\\\"key\\\":\\\"riskRegister\\\",\\\"enabled\\\":false}]";
        mvc.perform(put("/api/v1/report-templates/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sections\":\"" + sections + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sections").value(org.hamcrest.Matchers.containsString("riskRegister")));
    }
}
