package gateway.service.proxy;

import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ProxyClient {
  private final OkHttpClient proxyClient;

  public ProxyClient() {
    proxyClient =
        new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
            .addInterceptor(new ProxyInterceptor())
            .build();
  }

  public Response proxy(Request request) throws Exception {
    return proxyClient.newCall(request).execute();
  }
}
