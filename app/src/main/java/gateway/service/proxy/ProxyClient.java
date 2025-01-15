package gateway.service.proxy;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ProxyClient {
    private final OkHttpClient proxyClient;

    public ProxyClient() {
    proxyClient = new OkHttpClient().newBuilder()
            .addInterceptor(new ProxyLogging()).build();
    }

    public Response proxy(Request request) throws Exception {
        return proxyClient.newCall(request).execute();
    }
}
