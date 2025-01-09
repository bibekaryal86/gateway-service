package gateway.service.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUtils {
  private static final Logger log = LoggerFactory.getLogger(HttpUtils.class);

  public static HttpResponse sendRequest(
      final String url,
      final String method,
      final String requestBody,
      final Map<String, String> headers,
      final String authorization) {
    HttpURLConnection connection = null;
    final StringBuilder response = new StringBuilder();
    int responseCode = -1;

    try {
      // Create URL object
      final URL apiUrl = URI.create(url).toURL();
      connection = (HttpURLConnection) apiUrl.openConnection();
      connection.setRequestMethod(method);
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      // Set Authorization header if provided
      if (authorization != null && !authorization.isEmpty()) {
        connection.setRequestProperty("Authorization", authorization);
      }

      // Set additional custom headers if provided
      if (headers != null) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
      }

      // If there is a request body, write it to the connection
      if (requestBody != null && !requestBody.isEmpty()) {
        connection.setDoOutput(true);
        try (final OutputStream os = connection.getOutputStream()) {
          final byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
          os.write(input, 0, input.length);
        }
      }

      // Get the response code from the API
      responseCode = connection.getResponseCode();

      // Read the response body if available
      try (final BufferedReader br =
          new BufferedReader(
              new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
          response.append(line);
        }
      }
    } catch (IOException ex) {
      log.error("Error sending request: [{}]|[{}]", method, url, ex);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }

    return new HttpResponse(responseCode, response.toString());
  }

  public static String createBasicAuthHeader(final String username, final String password) {
    String credentials = username + ":" + password;
    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
  }

  // HttpResponse class to encapsulate response code and body
  public record HttpResponse(int statusCode, String responseBody) {}
}
