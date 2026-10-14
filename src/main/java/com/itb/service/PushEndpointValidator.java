package com.itb.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PushEndpointValidator {
    private final Set<String> allowedHosts;

    public PushEndpointValidator(
            @Value("${app.push.allowed-hosts:fcm.googleapis.com,updates.push.services.mozilla.com,*.push.services.mozilla.com,web.push.apple.com,*.notify.windows.com}")
            String[] allowedHosts) {
        this.allowedHosts = Arrays.stream(allowedHosts).map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        if (this.allowedHosts.contains("*")) {
            throw new IllegalArgumentException("Configure provedores push específicos.");
        }
    }

    public URI validate(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getRawUserInfo() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || endpoint.length() > 1500 || !isAllowedHost(host.toLowerCase(Locale.ROOT))) {
                throw invalid();
            }
            return uri;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    public void validateResolvedDestination(String endpoint) {
        URI uri = validate(endpoint);
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(a -> !isPublicAddress(a))) {
                throw invalid();
            }
        } catch (UnknownHostException ex) {
            throw invalid();
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 255;
            int second = bytes[1] & 255;
            return first != 0 && first < 224 && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 198 && (second == 18 || second == 19));
        }
        return (bytes[0] & 254) != 252; // IPv6 unique-local networks (fc00::/7).
    }

    private boolean isAllowedHost(String host) {
        return allowedHosts.stream().anyMatch(allowed -> allowed.equals(host)
                || (allowed.startsWith("*.") && host.endsWith(allowed.substring(1))
                        && host.length() > allowed.length() - 1));
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("Endpoint push inválido ou provedor não permitido.");
    }
}
