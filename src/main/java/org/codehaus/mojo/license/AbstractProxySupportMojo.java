package org.codehaus.mojo.license;

/*
 * #%L
 * License Maven Plugin
 * %%
 * Copyright (C) 2008 - 2011 CodeLutin, Codehaus, Tony Chemit
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Proxy;
import org.codehaus.mojo.license.api.CloseableHttpClientSupplier;
import org.codehaus.mojo.license.utils.MojoHelper;
import org.codehaus.mojo.license.utils.UrlRequester;
import org.codehaus.plexus.util.ReaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Abstract license with proxy support mojo.
 *
 * @author tchemit dev@tchemit.fr
 * @since 1.0
 */
public abstract class AbstractProxySupportMojo
    extends AbstractMojo
    implements CloseableHttpClientSupplier
{
    private static final Logger LOG = LoggerFactory.getLogger(AbstractProxySupportMojo.class);


    /**
     * Get declared proxies from the {@code settings.xml} file
     *
     * @since 2.0.1
     */
    @Parameter(defaultValue = "${settings.proxies}", readonly = true)
    private List<Proxy> proxies;

    /**
     * Connect timeout in milliseconds passed to the HTTP client when downloading licenses from remote URLs.
     *
     * @since 1.18
     */
    @Parameter(property = "license.connectTimeout", defaultValue = "5000")
    private int connectTimeout;

    /**
     * Socket timeout in milliseconds passed to the HTTP client when downloading licenses from remote URLs.
     *
     * @since 1.18
     */
    @Parameter(property = "license.socketTimeout", defaultValue = "5000")
    private int socketTimeout;

    /**
     * Connect request timeout in milliseconds passed to the HTTP client when downloading licenses from remote URLs.
     *
     * @since 1.18
     */
    @Parameter(property = "license.connectionRequestTimeout", defaultValue = "5000")
    private int connectionRequestTimeout;

    /**
     * @return active http proxy or null
     */
    private Proxy findActiveProxy() {
        for (Proxy proxy : proxies) {
            if (proxy.isActive() && "http".equals(proxy.getProtocol())) {
                return proxy;
            }
        }
        return null;
    }

    @Override
    public CloseableHttpClient createHttpClient() {

        final Proxy proxy = findActiveProxy();
        final RequestConfig.Builder configBuilder = RequestConfig.copy(RequestConfig.DEFAULT) //
            .setConnectTimeout(connectTimeout) //
            .setSocketTimeout(socketTimeout) //
            .setConnectionRequestTimeout(connectionRequestTimeout);

        if (proxy != null) {
            configBuilder.setProxy(new HttpHost(proxy.getHost(), proxy.getPort(), proxy.getProtocol()));
        }

        HttpClientBuilder clientBuilder = HttpClients.custom().setDefaultRequestConfig(configBuilder.build());
        if (proxy != null) {
            if (proxy.getUsername() != null && proxy.getPassword() != null) {
                final CredentialsProvider credsProvider = new BasicCredentialsProvider();
                final Credentials creds = new UsernamePasswordCredentials(proxy.getUsername(), proxy.getPassword());
                credsProvider.setCredentials(new AuthScope(proxy.getHost(), proxy.getPort()), creds);
                clientBuilder.setDefaultCredentialsProvider(credsProvider);
            }
            final String rawNonProxyHosts = proxy.getNonProxyHosts();
            if (rawNonProxyHosts != null) {
                final String[] nonProxyHosts = rawNonProxyHosts.split("|");
                if (nonProxyHosts.length > 0) {
                    final List<Pattern> nonProxyPatterns = new ArrayList<>();
                    for (String nonProxyHost : nonProxyHosts) {
                        final Pattern pat =
                            Pattern.compile(nonProxyHost.replaceAll("\\.", "\\\\.").replaceAll("\\*", ".*"),
                                Pattern.CASE_INSENSITIVE);
                        nonProxyPatterns.add(pat);
                    }
                    final HttpHost proxyHost = new HttpHost(proxy.getHost(), proxy.getPort());
                    final HttpRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxyHost) {

                        @Override
                        protected HttpHost determineProxy(HttpHost target, HttpRequest request, HttpContext context)
                            throws HttpException {
                            for (Pattern pattern : nonProxyPatterns) {
                                if (pattern.matcher(target.getHostName()).matches()) {
                                    return null;
                                }
                            }
                            return super.determineProxy(target, request, context);
                        }

                    };
                    clientBuilder.setRoutePlanner(routePlanner);
                }
            }
        }
        return clientBuilder.build();
    }
}
