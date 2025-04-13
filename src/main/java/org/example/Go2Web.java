package org.example;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class Go2Web {
    private static final int MAX_REDIRECTS = 5;
    private static final String CACHE_DIR = System.getProperty("user.home") + File.separator + ".go2web" + File.separator + "cache" + File.separator;
    private static final long CACHE_TTL = 3600000; // 1 hour in milliseconds

    public static void main(String[] args) {
        createCacheDir();

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
                    StringBuilder searchTerm = new StringBuilder(value);
                    for (int i = 2; i < args.length; i++) {
                        searchTerm.append(" ").append(args[i]);
                    }
                    handleSearchOption(searchTerm.toString());
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

    private static void createCacheDir() {
        File cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
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

    private static void handleSearchOption(String searchTerm) {
        try {
            String encodedSearchTerm = searchTerm.replace(" ", "+");
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedSearchTerm;

            System.out.println("Searching for: " + searchTerm);
            HttpResponse response = makeHttpRequest(searchUrl, 0);
            List<SearchResult> results = extractSearchResults(response.body);

            if (results.isEmpty()) {
                System.out.println("No search results found. Try another search term.");
                return;
            }

            int count = 0;
            for (SearchResult result : results) {
                if (count >= 10) break;
                System.out.println((++count) + ". " + result.title);
                System.out.println("   URL: " + result.url);
                if (!result.description.isEmpty()) {
                    System.out.println("   " + result.description);
                }
                System.out.println();
            }

            if (!results.isEmpty()) {
                System.out.print("Enter result number to view (or 0 to exit): ");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                try {
                    String input = reader.readLine();
                    int selection = Integer.parseInt(input);
                    if (selection > 0 && selection <= results.size()) {
                        handleUrlOption(results.get(selection - 1).url);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid selection");
                }
            }
        } catch (Exception e) {
            System.err.println("Error searching: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<SearchResult> extractSearchResults(String html) {
        List<SearchResult> results = new ArrayList<>();

        try {
            Pattern resultPattern = Pattern.compile("<div class=\"result[^\"]*\"[^>]*>(.*?)</div>\\s*</div>", Pattern.DOTALL);
            Matcher resultMatcher = resultPattern.matcher(html);

            while (resultMatcher.find()) {
                String resultHtml = resultMatcher.group(1);

                String url = "";
                Pattern urlPattern = Pattern.compile("href=\"([^\"]+)\"");
                Matcher urlMatcher = urlPattern.matcher(resultHtml);
                if (urlMatcher.find()) {
                    url = urlMatcher.group(1);

                    if (url.startsWith("/") || url.contains("duckduckgo.com/")) {
                        continue;
                    }
                }

                String title = "";
                Pattern titlePattern = Pattern.compile("<a[^>]*class=\"result__a\"[^>]*>(.*?)</a>", Pattern.DOTALL);
                Matcher titleMatcher = titlePattern.matcher(resultHtml);
                if (titleMatcher.find()) {
                    title = cleanHtmlContent(titleMatcher.group(1));
                }

                String description = "";
                Pattern descPattern = Pattern.compile("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL);
                Matcher descMatcher = descPattern.matcher(resultHtml);
                if (descMatcher.find()) {
                    description = cleanHtmlContent(descMatcher.group(1));
                }

                if (!url.isEmpty() && !title.isEmpty()) {
                    results.add(new SearchResult(title, url, description));
                }
            }

            if (results.isEmpty()) {
                Pattern altPattern = Pattern.compile("<div[^>]*>(.*?)<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?</div>", Pattern.DOTALL);
                Matcher altMatcher = altPattern.matcher(html);

                while (altMatcher.find()) {
                    String url = altMatcher.group(2);
                    String title = cleanHtmlContent(altMatcher.group(3));

                    if (url.startsWith("/") || url.contains("duckduckgo.com/") || url.contains("javascript:")) {
                        continue;
                    }

                    results.add(new SearchResult(title, url, ""));
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting search results: " + e.getMessage());
        }

        return results;
    }

    private static HttpResponse makeHttpRequest(String urlString, int redirectCount) throws IOException, URISyntaxException {
        if (redirectCount > MAX_REDIRECTS) {
            throw new IOException("Too many redirects");
        }

        // Check cache first
        String cacheKey = generateCacheKey(urlString);
        File cacheFile = new File(CACHE_DIR + cacheKey);

        if (cacheFile.exists() && isValidCache(cacheFile)) {
            System.out.println("Using cached version of: " + urlString);
            return readFromCache(cacheFile);
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
                    "Accept: text/html,application/xhtml+xml,application/json,*/*;q=0.8\r\n" +
                    "Accept-Language: en-US,en;q=0.5\r\n" +
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

            HttpResponse response = new HttpResponse(headers, body);

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

            // Save to cache
            saveToCache(cacheFile, response);

            return response;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private static String generateCacheKey(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).replaceAll("[^a-zA-Z0-9]", "_");
        } catch (NoSuchAlgorithmException e) {
            return url.replaceAll("[^a-zA-Z0-9]", "_");
        }
    }

    private static boolean isValidCache(File cacheFile) {
        return System.currentTimeMillis() - cacheFile.lastModified() < CACHE_TTL;
    }

    private static HttpResponse readFromCache(File cacheFile) throws IOException {
        String content = new String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8);
        int separatorIndex = content.indexOf("\r\n\r\n");
        if (separatorIndex == -1) {
            throw new IOException("Invalid cache format");
        }

        String headers = content.substring(0, separatorIndex + 4);
        String body = content.substring(separatorIndex + 4);

        return new HttpResponse(headers, body);
    }

    private static void saveToCache(File cacheFile, HttpResponse response) {
        try {
            String content = response.headers + response.body;
            Files.write(cacheFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Warning: Failed to cache response: " + e.getMessage());
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

    static class SearchResult {
        String title;
        String url;
        String description;

        SearchResult(String title, String url, String description) {
            this.title = title;
            this.url = url;
            this.description = description;
        }
    }
}