package com.mealapp.router;

import com.mealapp.util.HttpUtil;
import com.mealapp.util.JwtUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Router
 * ------
 * A small hand-rolled path router for com.sun.net.httpserver.HttpServer.
 * Supports ":param" path segments, optional required-role auth, and
 * centralizes error -> HTTP-status mapping so controllers can just throw.
 */
public final class Router implements HttpHandler {
    public interface Handler {
        void handle(HttpExchange exchange, Map<String, String> pathParams, RequestContext ctx) throws Exception;
    }

    /** Per-request info attached by the auth middleware. */
    public static final class RequestContext {
        public String userId;
        public String role;
        public String email;
    }

    private static final class Route {
        final String method;
        final Pattern pattern;
        final List<String> paramNames;
        final boolean requiresAuth;
        final String requiredRole; // null = any authenticated role
        final Handler handler;

        Route(String method, String path, boolean requiresAuth, String requiredRole, Handler handler) {
            this.method = method;
            this.requiresAuth = requiresAuth;
            this.requiredRole = requiredRole;
            this.handler = handler;
            this.paramNames = new ArrayList<>();
            StringBuilder regex = new StringBuilder("^");
            for (String segment : path.split("/")) {
                if (segment.isEmpty()) continue;
                if (segment.startsWith(":")) {
                    paramNames.add(segment.substring(1));
                    regex.append("/([^/]+)");
                } else {
                    regex.append("/").append(Pattern.quote(segment));
                }
            }
            regex.append("/?$");
            this.pattern = Pattern.compile(regex.toString());
        }
    }

    private final List<Route> routes = new ArrayList<>();
    private final JwtUtil jwtUtil;

    public Router(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public void publicRoute(String method, String path, Handler handler) {
        routes.add(new Route(method, path, false, null, handler));
    }

    public void authRoute(String method, String path, String requiredRole, Handler handler) {
        routes.add(new Route(method, path, true, requiredRole, handler));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("OPTIONS".equals(method)) {
            HttpUtil.sendNoContentForOptions(exchange);
            return;
        }

        for (Route route : routes) {
            if (!route.method.equals(method)) continue;
            Matcher m = route.pattern.matcher(path);
            if (!m.matches()) continue;

            Map<String, String> params = new LinkedHashMap<>();
            for (int i = 0; i < route.paramNames.size(); i++) {
                params.put(route.paramNames.get(i), m.group(i + 1));
            }

            RequestContext ctx = new RequestContext();
            if (route.requiresAuth) {
                Map<String, Object> claims = authenticate(exchange);
                if (claims == null) {
                    HttpUtil.sendError(exchange, 401, "Missing or invalid Authorization Bearer token");
                    return;
                }
                ctx.userId = String.valueOf(claims.get("userId"));
                ctx.role = String.valueOf(claims.get("role"));
                ctx.email = String.valueOf(claims.get("email"));
                if (route.requiredRole != null && !route.requiredRole.equals(ctx.role)) {
                    HttpUtil.sendError(exchange, 403, "Requires " + route.requiredRole + " role");
                    return;
                }
            }

            try {
                route.handler.handle(exchange, params, ctx);
            } catch (IllegalArgumentException e) {
                HttpUtil.sendError(exchange, 400, e.getMessage());
            } catch (IllegalStateException e) {
                HttpUtil.sendError(exchange, 409, e.getMessage());
            } catch (SecurityException e) {
                HttpUtil.sendError(exchange, 403, e.getMessage());
            } catch (SQLException e) {
                String sqlState = e.getSQLState();
                if ("23505".equals(sqlState)) {
                    HttpUtil.sendError(exchange, 409, "That record already exists.");
                } else if ("23503".equals(sqlState)) {
                    HttpUtil.sendError(exchange, 400, "Related record not found.");
                } else if ("23502".equals(sqlState)) {
                    HttpUtil.sendError(exchange, 400, "A required field is missing.");
                } else {
                    System.err.println("[error] SQL: " + e.getMessage());
                    HttpUtil.sendError(exchange, 500, "Database error");
                }
            } catch (Exception e) {
                System.err.println("[error] " + e);
                e.printStackTrace();
                HttpUtil.sendError(exchange, 500, e.getMessage() == null ? "Internal server error" : e.getMessage());
            }
            return;
        }

        HttpUtil.sendError(exchange, 404, "Route not found: " + method + " " + path);
    }

    private Map<String, Object> authenticate(HttpExchange exchange) {
        List<String> headers = exchange.getRequestHeaders().get("Authorization");
        if (headers == null || headers.isEmpty()) return null;
        String header = headers.get(0);
        if (!header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        return jwtUtil.verify(token);
    }
}
