package org.codehaus.mojo.license.api;

import org.apache.http.impl.client.CloseableHttpClient;

public interface CloseableHttpClientSupplier {
    CloseableHttpClient createHttpClient();
}
