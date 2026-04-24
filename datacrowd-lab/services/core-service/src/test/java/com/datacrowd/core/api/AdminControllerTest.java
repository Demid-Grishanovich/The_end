package com.datacrowd.core.api;

import com.datacrowd.core.dto.RoleChangeRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AdminControllerTest {

    @Test
    void roleChangeRequest_acceptsValidRoles() {
        RoleChangeRequest req = new RoleChangeRequest();

        req.role = "WORKER";
        assertThat(req.role).isEqualTo("WORKER");

        req.role = "CLIENT";
        assertThat(req.role).isEqualTo("CLIENT");

        req.role = "ADMIN";
        assertThat(req.role).isEqualTo("ADMIN");

        req.role = "REVIEWER";
        assertThat(req.role).isEqualTo("REVIEWER");
    }

    @Test
    void roleChangeRequest_canBeCreated() {
        RoleChangeRequest req = new RoleChangeRequest();
        assertThat(req).isNotNull();
        assertThat(req.role).isNull();
    }
}