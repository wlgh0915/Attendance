package com.company.attendancemanagement.interceptor;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.mapper.RoleMenuMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MenuPermissionInterceptor implements HandlerInterceptor {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/login",
            "/logout",
            "/error",
            "/favicon.ico"
    );

    private static final List<String> STATIC_PREFIXES = List.of(
            "/css/",
            "/js/",
            "/images/",
            "/img/",
            "/webjars/"
    );

    private final RoleMenuMapper roleMenuMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = normalizeUri(request.getRequestURI(), request.getContextPath());
        if (isPublicPath(uri) || isStaticResource(uri)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        LoginUserDto loginUser = session == null
                ? null
                : (LoginUserDto) session.getAttribute(SessionConst.LOGIN_USER);
        if (loginUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        List<String> permittedUrls = roleMenuMapper.findPermittedMenuUrls(
                loginUser.getCompany(), loginUser.getRoleCode());
        if (isPermitted(uri, permittedUrls)) {
            return true;
        }

        reject(request, response);
        return false;
    }

    private String normalizeUri(String requestUri, String contextPath) {
        String uri = requestUri;
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri.isBlank() ? "/" : uri;
    }

    private boolean isPublicPath(String uri) {
        return PUBLIC_PATHS.contains(uri);
    }

    private boolean isStaticResource(String uri) {
        return STATIC_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    private boolean isPermitted(String uri, List<String> permittedUrls) {
        if (permittedUrls == null || permittedUrls.isEmpty()) {
            return false;
        }

        for (String permittedUrl : permittedUrls) {
            if (matchesMenuUrl(uri, permittedUrl)) {
                return true;
            }
        }

        for (String relatedMenuUrl : relatedMenuUrls(uri)) {
            for (String permittedUrl : permittedUrls) {
                if (matchesMenuUrl(relatedMenuUrl, permittedUrl)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean matchesMenuUrl(String uri, String menuUrl) {
        if (menuUrl == null || menuUrl.isBlank()) {
            return false;
        }
        String normalizedMenuUrl = normalizeMenuUrl(menuUrl);
        if (uri.equals(normalizedMenuUrl)) {
            return true;
        }
        if (!normalizedMenuUrl.contains("{")) {
            return false;
        }

        String regex = Pattern.quote(normalizedMenuUrl)
                .replace("\\{", "{")
                .replace("\\}", "}")
                .replaceAll("\\{[^/]+}", "\\\\E[^/]+\\\\Q");
        return Pattern.matches(regex, uri);
    }

    private String normalizeMenuUrl(String menuUrl) {
        String normalized = menuUrl.trim();
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private List<String> relatedMenuUrls(String uri) {
        List<String> candidates = new ArrayList<>();

        if (uri.startsWith("/attendance/approval/")) {
            candidates.add("/attendance/approval");
        }
        if (uri.startsWith("/attendance/calendar/")) {
            candidates.add("/attendance/calendar");
        }
        if (uri.startsWith("/attendance/record/")) {
            candidates.add("/attendance/record");
        }
        if (uri.startsWith("/attendance/department/week/")) {
            candidates.add("/attendance/department/week");
        }
        if (uri.startsWith("/attendance/department/month/")) {
            candidates.add("/attendance/department/month");
        }

        if (uri.startsWith("/attendance/request/general/")) {
            candidates.add("/attendance/request/general");
        }
        if (uri.startsWith("/attendance/request/other/")) {
            candidates.add("/attendance/request/other");
        }
        if (uri.startsWith("/attendance/request/history/")) {
            candidates.add("/attendance/request/history");
        }
        if (uri.startsWith("/attendance/request/")
                && !uri.startsWith("/attendance/request/general")
                && !uri.startsWith("/attendance/request/other")
                && !uri.startsWith("/attendance/request/history")) {
            candidates.add("/attendance/request/general");
            candidates.add("/attendance/request/other");
        }

        if (uri.startsWith("/pattern/save") || uri.startsWith("/pattern/delete")) {
            candidates.add("/pattern/calendar");
            candidates.add("/pattern/new");
            candidates.add("/pattern/edit/{workPatternCode}");
        }

        if (uri.startsWith("/departments/employees/move")) {
            candidates.add("/departments/employees");
            candidates.add("/departments/employees/unassigned-manage");
        }
        if (uri.startsWith("/departments/employees/add")) {
            candidates.add("/departments/employees/unassigned");
        }

        return candidates;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (expectsJson(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"접근 권한이 없습니다.\"}");
            return;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다.");
    }

    private boolean expectsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE));
    }
}
