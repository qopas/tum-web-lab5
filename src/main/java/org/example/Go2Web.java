package org.example;

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
                    System.out.println("URL option is not implemented yet.");
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
}