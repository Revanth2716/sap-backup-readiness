package com.revzz.sap.readiness.web;

import com.revzz.sap.readiness.config.AppProperties;
import com.revzz.sap.readiness.domain.AppUser;
import com.revzz.sap.readiness.repo.PlatformRepository;
import com.revzz.sap.readiness.service.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/** Sign in and sign out. */
@Controller
public class AuthController {

    private final PlatformRepository platform;
    private final PasswordEncoder encoder;
    private final AuditService auditService;
    private final AppProperties properties;

    public AuthController(PlatformRepository platform, PasswordEncoder encoder,
                          AuditService auditService, AppProperties properties) {
        this.platform = platform;
        this.encoder = encoder;
        this.auditService = auditService;
        this.properties = properties;
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "next", required = false) String next,
                        Model model) {
        model.addAttribute("error", error);
        model.addAttribute("next", next);
        model.addAttribute("demoPassword", properties.getDemoPassword());
        return "login";
    }

    @PostMapping("/login")
    public String authenticate(@RequestParam String username,
                               @RequestParam String password,
                               @RequestParam(value = "next", required = false) String next,
                               HttpServletRequest request) {
        AppUser user = platform.findAccount(username);
        if (user == null || !user.isEnabled()
                || !encoder.matches(password, user.getPasswordHash())) {
            auditService.record(username, "LOGIN_FAILED", "Invalid credentials");
            return "redirect:/login?error=invalid";
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(CurrentUser.SESSION_ATTRIBUTE, user.getRole().name());
        session.setAttribute("DISPLAY_NAME", user.getDisplayName());
        auditService.record(user.getUsername(), "LOGIN",
                "Signed in with role " + user.getRole().name());

        if (next != null && next.startsWith("/")) {
            return "redirect:" + next;
        }
        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            auditService.record(CurrentUser.username(session), "LOGOUT", "Signed out");
            session.invalidate();
        }
        return "redirect:/login";
    }
}
