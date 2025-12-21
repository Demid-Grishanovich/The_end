package com.datacrowd.core.api;

import com.datacrowd.core.security.AuthContext;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@RestController
@RequestMapping("/core/security-demo")
public class SecurityDemoController {

    @GetMapping("/me")
    public Object me(Authentication authentication) {
        var res = new HashMap<String, Object>();

        if (authentication == null || authentication.getPrincipal() == null) {
            res.put("authenticated", false);
            return res;
        }

        res.put("authenticated", true);
        res.put("principalClass", authentication.getPrincipal().getClass().getName());
        res.put("name", authentication.getName());
        res.put("authorities", authentication.getAuthorities().toString());

        AuthContext.getUserId().ifPresentOrElse(
                id -> res.put("userId", id.toString()),
                () -> res.put("userId", null)
        );

        return res;
    }

    @GetMapping("/worker")
    public String workerOnly() { return "WORKER OK"; }

    @GetMapping("/client")
    public String clientOnly() { return "CLIENT OK"; }

    @GetMapping("/admin")
    public String adminOnly() { return "ADMIN OK"; }
}
