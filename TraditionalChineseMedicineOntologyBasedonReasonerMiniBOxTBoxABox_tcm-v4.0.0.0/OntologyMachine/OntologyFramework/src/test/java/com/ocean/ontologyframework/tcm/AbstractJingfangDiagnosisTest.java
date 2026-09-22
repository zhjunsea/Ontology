package com.ocean.ontologyframework.tcm;

import com.ocean.ontologyframework.StopOnTimeoutExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

@ExtendWith(StopOnTimeoutExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 100, unit = TimeUnit.SECONDS)
public abstract class AbstractJingfangDiagnosisTest {

    @BeforeAll
    static void initFramework() {
        JingfangTestSupport.ensureInitialized();
    }
}