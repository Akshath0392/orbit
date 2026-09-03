package com.orbit.controller;

import com.orbit.repository.AppUserRepository;
import com.orbit.service.dashboard.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService svc;
    private final AppUserRepository appUsers;
    public DashboardController(DashboardService svc, AppUserRepository appUsers) {
        this.svc = svc; this.appUsers = appUsers;
    }

    @GetMapping("/radar")
    @PreAuthorize("hasAnyRole('PM','LEADERSHIP','ADMIN')")
    public Object radar() { return svc.getRadar(); }

    @GetMapping("/cockpit")
    @PreAuthorize("isAuthenticated()")
    public Object cockpit(Authentication auth) {
        Long userId = appUsers.findByEmail(auth.getName()).map(u -> u.getId()).orElse(null);
        return svc.getCockpit(userId);
    }
}
