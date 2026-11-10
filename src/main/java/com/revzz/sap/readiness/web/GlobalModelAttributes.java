package com.revzz.sap.readiness.web;

import com.revzz.sap.readiness.config.AppProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpSession;

/** Values every page needs. */
@ControllerAdvice
public class GlobalModelAttributes {

    private final AppProperties properties;

    public GlobalModelAttributes(AppProperties properties) {
        this.properties = properties;
    }

    @ModelAttribute("appName")
    public String appName() {
        return properties.getApplicationName();
    }

    @ModelAttribute("sapSystem")
    public String sapSystem() {
        return properties.getSapSystem();
    }

    @ModelAttribute("currentRole")
    public String currentRole(HttpSession session) {
        return CurrentUser.role(session);
    }

    @ModelAttribute("displayName")
    public String displayName(HttpSession session) {
        if (session == null) {
            return "Not signed in";
        }
        Object value = session.getAttribute("DISPLAY_NAME");
        return value == null ? "Not signed in" : value.toString();
    }
}
