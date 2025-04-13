package org.example;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class Go2Web {
    private static final int MAX_REDIRECTS = 5;

    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equals("-h"))) {
            printHelp();
            return;
        }

        if (args.length >= 2) {
            String option = args[0];
            String value = args[1];

            switch (option) {
                case "-u":
                    handleUrlOption(value);
                    break;
                case "-s":
                    System.out.println("Search option is not implemented yet.");
                    break;
                default:
                    System.err.println("Unknown option: " + option);
                    printHelp();
            }
        } else {
            System.err.println("Invalid arguments");
            printHelp();
        }
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("go2web -u <URL>         # make an HTTP request to the specified URL and print the response");
        System.out.println("go2web -s <search-term> # make an HTTP request to search the term using your favorite search engine and print top 10 results");
        System.out.println("go2web -h               # show this help");
    }

    private static void handleUrlOption(String url) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
            HttpResponse response = makeHttpRequest(url, 0);
            System.out.println(cleanHtmlContent(response.body));
        } catch (Exception e) {
            System.err.println("Error making HTTP request: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static HttpResponse makeHttpRequest(String urlString, int redirectCount) throws IOException, URISyntaxException {
        if (redirectCount > MAX_REDIRECTS) {
            throw new IOException("Too many redirects");
        }

        URI uri = new URI(urlString);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? (uri.getScheme().equals("https") ? 443 : 80) : uri.getPort();
        String path = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }

        Socket socket = null;
        try {
            if (uri.getScheme().equals("https")) {
                SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                socket = sslSocketFactory.createSocket(host, port);
            } else {
                socket = new Socket(host, port);
            }

            String request = "GET " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36\r\n" +
                    "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n" +
                    "Connection: close\r\n\r\n";

            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = socket.getInputStream();

            ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                responseBytes.write(buffer, 0, bytesRead);
            }

            String fullResponse = responseBytes.toString(StandardCharsets.UTF_8.name());

            int headerEnd = fullResponse.indexOf("\r\n\r\n");
            if (headerEnd == -1) {
                throw new IOException("Invalid HTTP response format");
            }

            String headers = fullResponse.substring(0, headerEnd + 4);
            String body = fullResponse.substring(headerEnd + 4);

            if (headers.contains("HTTP/1.1 301") || headers.contains("HTTP/1.1 302") ||
                    headers.contains("HTTP/1.1 303") || headers.contains("HTTP/1.1 307") ||
                    headers.contains("HTTP/1.1 308") ||
                    headers.contains("HTTP/1.0 301") || headers.contains("HTTP/1.0 302")) {

                String locationHeader = extractHeader(headers, "Location");
                if (locationHeader != null) {
                    if (locationHeader.startsWith("/")) {
                        locationHeader = uri.getScheme() + "://" + uri.getHost() + locationHeader;
                    }
                    System.out.println("Redirecting to: " + locationHeader);
                    return makeHttpRequest(locationHeader, redirectCount + 1);
                }
            }

            return new HttpResponse(headers, body);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private static String extractHeader(String headers, String headerName) {
        Pattern pattern = Pattern.compile(headerName + ": (.+?)\\r\\n", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(headers);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String cleanHtmlContent(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        html = html.replaceAll("<!DOCTYPE[^>]*>", "");
        html = html.replaceAll("(?s)<script.*?</script>", "");
        html = html.replaceAll("(?s)<style.*?</style>", "");
        html = html.replaceAll("(?s)<!--.*?-->", "");
        html = html.replaceAll("<[^>]*>", " ");

        html = html.replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&apos;", "'")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&#[0-9]+;", "")
                .replaceAll("&[a-zA-Z]+;", " ");

        html = html.replaceAll("\\s+", " ");

        html = html.replaceAll("\\. ", ".\n");

        return html.trim();
    }

    static class HttpResponse {
        String headers;
        String body;

        HttpResponse(String headers, String body) {
            this.headers = headers;
            this.body = body;
        }
    }
}