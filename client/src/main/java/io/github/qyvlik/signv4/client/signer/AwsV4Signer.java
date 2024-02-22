package io.github.qyvlik.signv4.client.signer;

import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import io.github.qyvlik.signv4.domain.hash.LocalSignatory;
import io.github.qyvlik.signv4.domain.model.CanonicalRequest;
import io.github.qyvlik.signv4.domain.model.Credential;
import io.github.qyvlik.signv4.domain.model.Signing;
import okhttp3.Request;
import okio.Buffer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class AwsV4Signer {

    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private AwsV4Signer() {
    }

    public static Signing signing(Request request,
                                  String accessKey,
                                  String secretKey,
                                  String requestDateTime,
                                  String region,
                                  String service) throws IOException {
        String method = request.method();
        String uri = request.url().encodedPath().replace("/+", "/");
        String query = getSortedQueryString(request);
        String headers = getSortedHeadersString(request);
        String signedHeaders = getSortedSignedHeadersString(request);
        byte[] body = bodyAsBytes(request);
        String hashedPayload = Hashing.sha256().hashBytes(body).toString();

        String date = requestDateTime.substring(0, 7);

        return Signing.signing(
                new CanonicalRequest(
                        method,
                        uri,
                        query,
                        headers,
                        signedHeaders,
                        hashedPayload
                ),
                new Credential(
                        accessKey,
                        date,
                        region,
                        service,
                        "aws4_request"
                ),
                "AWS4-HMAC-SHA256",
                requestDateTime,
                LocalSignatory.create(secretKey)
        );
    }

    public static String getSortedQueryString(Request request) {
        var url = request.url();
        Set<String> queryParameterNames =
                url.queryParameterNames();
        if (queryParameterNames.isEmpty()) {
            return "";
        }
        TreeMap<String, String> map = new TreeMap<>();
        for (String key : url.queryParameterNames()) {
            String value = url.queryParameter(key);
            map.put(rfc3986Encode(key), rfc3986Encode(value));
        }
        return sortedMapToString(map, "=", "&", "");
    }

    public static String getSortedHeadersString(Request request) {
        var headers = request.headers();
        var headerNames = headers.names();
        if (headerNames.isEmpty()) {
            return "\n";
        }
        TreeMap<String, String> map = new TreeMap<>();
        for (String headerName : headerNames) {
            if (headerName.equalsIgnoreCase("Authorization")) {
                continue;
            }
            String headerValue = request.header(headerName);
            if (StringUtils.isBlank(headerValue)) {
                headerValue = "";
            }
            headerValue = headerValue.trim();
            map.put(headerName.toLowerCase(Locale.ENGLISH), headerValue);
        }
        return sortedMapToString(map, ":", "\n", "\n");
    }

    public static String getSortedSignedHeadersString(Request request) {
        var headers = request.headers();
        var headerNames = headers.names();
        if (headerNames.isEmpty()) {
            return "\n";
        }
        TreeSet<String> set = new TreeSet<>();
        for (String headerName : headerNames) {
            if (headerName.equalsIgnoreCase("Authorization")) {
                continue;
            }
            set.add(headerName.toLowerCase(Locale.ENGLISH));
        }
        return String.join(";", set);
    }

    public static String sortedMapToString(SortedMap<String, String> map, String assign, String delimiter, String ending) {
        List<String> list = Lists.newArrayList();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            list.add(entry.getKey() + assign + entry.getValue());
        }
        return String.join(delimiter, list) + ending;
    }

    public static String rfc3986Encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    public static byte[] bodyAsBytes(Request request) throws IOException {
        var body = request.body();
        if (body == null) {
            return new byte[0];
        }
        var buffer = new Buffer();
        body.writeTo(buffer);
        return buffer.readByteArray();
    }
}