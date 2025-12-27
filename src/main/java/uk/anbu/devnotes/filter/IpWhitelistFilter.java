package uk.anbu.devnotes.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class IpWhitelistFilter implements Filter {

    @Value("#{'${devnotes.allowedIps}'.split(',')}")
    private List<String> allowedIps;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String remoteIp = request.getRemoteAddr();
        if (allowedIps.contains(remoteIp)) {
            chain.doFilter(request, response);
        } else {
            // No response: just close the connection
            ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().close();
            log.warn("Blocked request from IP: {}", remoteIp);
        }
    }
}