import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class Request {
    private static List<NameValuePair> params;
    private static List<NameValuePair> bodyParams;
    private static List<String> headers;
    private final String method;
    private final String path;

    public Request(String method, String path, List<NameValuePair> params, List<String> headers, List<NameValuePair> bodyParams) {
        this.method = method;
        this.path = path;
        this.params = params;
        this.headers = headers;
        this.bodyParams = bodyParams;
    }

    public static List<NameValuePair> getQueryParams(String name) {
        return params = URLEncodedUtils.parse(name, StandardCharsets.UTF_8);
    }

    public static void getQueryParam(List<NameValuePair> params, String key) {
        for (NameValuePair parameter : params) {
            if (parameter.getName().equals(key)) {
                System.out.println(parameter.getName() + " = " + parameter.getValue());
            }
        }
    }

    public static List<NameValuePair> getPostParams(String name) {
        return bodyParams = URLEncodedUtils.parse(name, StandardCharsets.UTF_8);
    }

    public static void getPostParam(String name, List<NameValuePair> bodyParams) {
        for (NameValuePair parameter : bodyParams) {
            if (parameter.getName().equals(name)) {
                System.out.println(parameter.getName() + " = " + parameter.getValue());
            }
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(method).append(" ").append(path).append(" ");
        if (params != null) {
            sb.append(params);
        }
        sb.append("\n").append(headers).append("\n").append("\n");
        if (bodyParams != null) {
            sb.append(bodyParams);
        }
        return sb.toString();
    }
}

