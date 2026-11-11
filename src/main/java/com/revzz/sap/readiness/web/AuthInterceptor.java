package com.revzz.sap.readiness.web;

import com.revzz.sap.readiness.config.StartupGate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Session-based authentication and role gating.
 *
 * <p>Small and readable on purpose: three roles, one interceptor, one
 * annotation. The REST API is authenticated inside the API controller
 * itself, so that it can answer with a JSON 401 rather than a redirect to a
 * login form.</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final StartupGate gate;

    public AuthInterceptor(StartupGate gate) {
        this.gate = gate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // The port opens before the seeders have run. Until they have, the
        // application has nothing worth showing, so it says so rather than
        // serving a half-built landscape.
        if (!gate.isReady()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The application is still starting up - try again in a moment.");
            return false;
        }

        if (request.getRequestURI().startsWith("/api/")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        String username = CurrentUser.username(session);
        if ("anonymous".equals(username)) {
            response.sendRedirect(request.getContextPath() + "/login?next="
                    + request.getRequestURI());
            return false;
        }

        if (handler instanceof HandlerMethod) {
            RequireRole required = ((HandlerMethod) handler).getMethodAnnotation(RequireRole.class);
            if (required == null) {
                required = ((HandlerMethod) handler).getBeanType().getAnnotation(RequireRole.class);
            }
            if (required != null && !hasAnyRole(session, required.value())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Your role does not grant access to this screen.");
                return false;
            }
        }
        return true;
    }

    private boolean hasAnyRole(HttpSession session, String[] allowed) {
        Object value = session.getAttribute(CurrentUser.SESSION_ATTRIBUTE);
        String role = value == null ? "" : value.toString();
        for (String candidate : allowed) {
            if (candidate.equals(role)) {
                return true;
            }
        }
        return false;
    }
}
