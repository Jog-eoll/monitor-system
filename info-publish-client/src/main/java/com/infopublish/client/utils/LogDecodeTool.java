package com.infopublish.client.utils;

import com.infopublish.client.utils.log.EncodedLogOutputStream;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Decodes ENC1 log files back to plain text.
 */
public class LogDecodeTool {

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static int run(String[] args) {
        if (args == null || args.length != 2) {
            System.err.println("Usage: LogDecodeTool <input.log> <output.log>");
            return 1;
        }

        try {
            Path inputPath = Paths.get(args[0]).toAbsolutePath().normalize();
            Path outputPath = Paths.get(args[1]).toAbsolutePath().normalize();
            if (inputPath.equals(outputPath)) {
                System.err.println("input and output file must be different: " + inputPath);
                return 1;
            }

            decodeFile(inputPath, outputPath);
            System.out.println("decoded log saved: " + outputPath);
            return 0;
        } catch (Exception e) {
            System.err.println("decode log failed: " + e.getMessage());
            return 1;
        }
    }

    private static void decodeFile(Path inputPath, Path outputPath) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                writer.write(decodeLine(line, lineNumber));
                writer.newLine();
            }
        }
    }

    private static String decodeLine(String line, long lineNumber) {
        if (line == null || !line.startsWith(EncodedLogOutputStream.PREFIX)) {
            return line;
        }

        String payload = line.substring(EncodedLogOutputStream.PREFIX.length());
        try {
            byte[] plainBytes = Base64.getDecoder().decode(payload);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            System.err.println("failed to decode line " + lineNumber + ": " + e.getMessage());
            return line;
        }
    }
}
