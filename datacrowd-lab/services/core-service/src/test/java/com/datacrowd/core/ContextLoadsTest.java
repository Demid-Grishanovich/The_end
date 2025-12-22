package com.datacrowd.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Core-service requires infra (DB/Flyway/Security). Use unit + web (standalone MockMvc) tests instead.")
class ContextLoadsTest {

    @Test
    void contextLoads() {
    }
}
