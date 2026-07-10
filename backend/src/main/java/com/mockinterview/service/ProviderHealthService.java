package com.mockinterview.service;

import com.mockinterview.config.properties.AssemblyAiProperties;
import com.mockinterview.service.ai.AIProviderRouter;
import com.mockinterview.config.properties.Judge0Properties;
import com.mockinterview.config.properties.MurfProperties;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

@Service
public class ProviderHealthService {

    private final AIProviderRouter aiProvider;
    private final AssemblyAiProperties assemblyAi;
    private final MurfProperties murf;
    private final Judge0Properties judge0;
    private final DataSource dataSource;

    public ProviderHealthService(AIProviderRouter aiProvider, AssemblyAiProperties assemblyAi,
                                 MurfProperties murf, Judge0Properties judge0,
                                 DataSource dataSource) {
        this.aiProvider = aiProvider;
        this.assemblyAi = assemblyAi;
        this.murf = murf;
        this.judge0 = judge0;
        this.dataSource = dataSource;
    }

    public Map<String, Object> getHealthStatus() {
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("status", "UP");
        
        boolean gOk = aiProvider.isHealthy();
        Map<String, Object> gMap = new java.util.LinkedHashMap<>();
        gMap.put("configured", gOk);
        gMap.put("questionGeneration", gOk);
        gMap.put("resumeAnalysis", gOk);
        gMap.put("scoring", gOk);
        status.put("aiProviders", gMap);
        
        boolean jOk = judge0.isConfigured();
        Map<String, Object> jMap = new java.util.LinkedHashMap<>();
        jMap.put("configured", jOk);
        jMap.put("execution", jOk);
        status.put("judge0", jMap);
        
        boolean aOk = assemblyAi.isConfigured();
        Map<String, Object> aMap = new java.util.LinkedHashMap<>();
        aMap.put("configured", aOk);
        aMap.put("speechToText", aOk);
        status.put("assemblyAI", aMap);
        
        boolean mOk = murf.isConfigured();
        Map<String, Object> mMap = new java.util.LinkedHashMap<>();
        mMap.put("configured", mOk);
        mMap.put("textToSpeech", mOk);
        status.put("murf", mMap);
        
        boolean dbUp = false;
        try (Connection conn = dataSource.getConnection()) {
            dbUp = conn.isValid(2);
        } catch (SQLException e) {
            dbUp = false;
        }
        Map<String, Object> dbMap = new java.util.LinkedHashMap<>();
        dbMap.put("configured", true);
        dbMap.put("status", dbUp ? "UP" : "DOWN");
        status.put("database", dbMap);
        
        return status;
    }
}

