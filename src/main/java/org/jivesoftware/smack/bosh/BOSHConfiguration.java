/**
 * Copyright 2009 Jive Software.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smack.bosh;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.message.BasicHeader;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.proxy.ProxyInfo;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration to use while establishing the connection to the XMPP server via
 * HTTP binding.
 *
 * @author Guenther Niess
 */
public final class BOSHConfiguration extends ConnectionConfiguration {

    private final boolean https;
    private final String file;
    private List<Header> httpHeaders;

    private BOSHConfiguration(Builder builder) {
        super(builder);
        if (proxy != null) {
            if (proxy.getProxyType() != ProxyInfo.ProxyType.HTTP) {
                throw new IllegalArgumentException(
                        "Only HTTP proxies are support with BOSH connections");
            }
        }
        https = builder.https;
        if (builder.file.charAt(0) != '/') {
            file = '/' + builder.file;
        } else {
            file = builder.file;
        }
        httpHeaders = builder.httpHeaders;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isProxyEnabled() {
        return proxy != null;
    }

    @Override
    public ProxyInfo getProxyInfo() {
        return proxy;
    }

    public String getProxyAddress() {
        return (proxy != null ? proxy.getProxyAddress() : null);
    }

    public int getProxyPort() {
        return (proxy != null ? proxy.getProxyPort() : 8080);
    }

    public boolean isUsingHTTPS() {
        return https;
    }

    public URI getURI() throws URISyntaxException {
        return new URI((https ? "https://" : "http://") + this.host + ":" + this.port + file);
    }

    public List<Header> getHttpHeaders() {
        return httpHeaders;
    }

    public static final class Builder extends ConnectionConfiguration.Builder<Builder, BOSHConfiguration> {
        private boolean https;
        private String file;
        private List<Header> httpHeaders = new ArrayList<>();

        private Builder() {
        }

        public Builder setUseHttps(boolean useHttps) {
            https = useHttps;
            return this;
        }

        public Builder useHttps() {
            return setUseHttps(true);
        }

        public Builder setFile(String file) {
            this.file = file;
            return this;
        }

        public Builder setServiceUrl(URL serviceUrl) {
            this.setUseHttps(serviceUrl.getProtocol().equalsIgnoreCase("https"))
                    .setHost(serviceUrl.getHost())
                    .setPort(serviceUrl.getPort() == -1 ? serviceUrl.getDefaultPort() : serviceUrl.getPort())
                    .setFile(serviceUrl.getFile())
                    .addUserInfo(serviceUrl.getUserInfo());
            return this;
        }

        public Builder addHttpHeader(Header header) {
            httpHeaders.add(header);
            return this;
        }

        private Builder addUserInfo(String userInfo) {
            if(null == userInfo) {
                return this;
            }
            String[] up = userInfo.split(":");
            if (up.length != 2) {
                throw new RuntimeException("Invalid user info in URL: " + userInfo);
            }
            try {
                String username = URLDecoder.decode(up[0], "UTF-8");
                String password = URLDecoder.decode(up[1], "UTF-8");
                String auth = username + ":" + password;
                byte[] encodedAuth = Base64.encodeBase64(
                        auth.getBytes(StandardCharsets.ISO_8859_1));
                String authHeader = "Basic " + new String(encodedAuth);

                this.addHttpHeader(new BasicHeader(HttpHeaders.AUTHORIZATION, authHeader));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            return this;
        }


        @Override
        public BOSHConfiguration build() {
            return new BOSHConfiguration(this);
        }

        @Override
        protected Builder getThis() {
            return this;
        }
    }
}