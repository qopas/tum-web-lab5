package org.example;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public class Go2Web {
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
            HttpResponse response = makeHttpRequest(url);
            System.out.println(cleanHtmlContent(response.body));
        } catch (Exception e) {
            System.err.println("Error making HTTP request: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static HttpResponse makeHttpRequest(String urlString) throws IOException, URISyntaxException {
        URI uri = new URI(urlString);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 80 : uri.getPort();
        String path = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }

        Socket socket = null;
        try {
            socket = new Socket(host, port);

            String request = "GET " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "User-Agent: go2web/1.0\r\n" +
                    "Connection: close\r\n\r\n";

            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            StringBuilder headerBuilder = new StringBuilder();
            StringBuilder bodyBuilder = new StringBuilder();
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    if (line.isEmpty()) {
                        isHeader = false;
                    } else {
                        headerBuilder.append(line).append("\r\n");
                    }
                } else {
                    bodyBuilder.append(line).append("\n");
                }
            }

            return new HttpResponse(headerBuilder.toString(), bodyBuilder.toString());
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
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