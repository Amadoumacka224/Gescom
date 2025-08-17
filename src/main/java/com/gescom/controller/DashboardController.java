package com.gescom.controller;

import com.gescom.dto.DashboardDto;
import com.gescom.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails,
                            @RequestParam(defaultValue = "30") int days,
                            Model model) {

        // Récupérer les données selon le rôle
        DashboardDto dashboardData = dashboardService.getDashboardData(userDetails.getUsername(), days);
        model.addAttribute("dashboardData", dashboardData);
        model.addAttribute("selectedPeriod", days);

        // Rediriger vers le bon template selon le rôle
        if (userDetails.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return "dashboard/admin";
        } else if (userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"))) {
            return "dashboard/manager";
        } else {
            return "dashboard/user";
        }
    }

    @GetMapping("/stats")
    public String getStats(@AuthenticationPrincipal UserDetails userDetails,
                           @RequestParam String type,
                           @RequestParam(defaultValue = "30") int days,
                           Model model) {

        Object stats = dashboardService.getStatsByType(userDetails.getUsername(), type, days);
        model.addAttribute("stats", stats);

        return "fragments/stats :: " + type + "Stats";
    }
}