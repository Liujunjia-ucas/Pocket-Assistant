package com.codexpocket.app;

import java.net.URI;

final class DirectEndpoint {
    static String normalize(String value) throws Exception {
        String base = value.trim().replaceAll("/+$", "");
        URI uri = new URI(base);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new Exception("请填写 HTTPS API 地址，例如 https://api.deepseek.com/v1");
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.endsWith("/pricing") || path.endsWith("/usage") || path.contains("/console"))
            throw new Exception("这里需要 API 地址，不能填写价格页或用量页。");
        for (String suffix : new String[]{"/chat/completions", "/responses", "/models"})
            if (base.endsWith(suffix)) { base = base.substring(0, base.length() - suffix.length()); break; }
        if (new URI(base).getPath().isEmpty()) base += "/v1";
        return base;
    }
    static boolean sameOrigin(String a, String b) throws Exception {
        URI x = new URI(a), y = new URI(b);
        return x.getHost().equalsIgnoreCase(y.getHost()) && x.getPort() == y.getPort();
    }
    private DirectEndpoint() {}
}
