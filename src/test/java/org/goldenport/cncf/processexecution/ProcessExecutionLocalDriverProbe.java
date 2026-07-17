package org.goldenport.cncf.processexecution;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Controlled host-process fixture for LocalProcessExecutionDriverSpec.
 * It depends only on the JDK so the spawned process has a stable test classpath.
 */
public final class ProcessExecutionLocalDriverProbe {
    private ProcessExecutionLocalDriverProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        String mode = arguments.length == 0 ? "" : arguments[0];
        switch (mode) {
            case "streams" -> {
                String value = arguments.length > 1 ? arguments[1] : "";
                System.out.print("stdout:" + value);
                System.out.flush();
                System.err.print("stderr");
                System.err.flush();
            }
            case "exit" -> {
                System.err.print("nonzero");
                System.err.flush();
                System.exit(arguments.length > 1 ? Integer.parseInt(arguments[1]) : 1);
            }
            case "sleep" -> Thread.sleep(arguments.length > 1 ? Long.parseLong(arguments[1]) : 10000L);
            case "flood-stdout" -> flood(System.out);
            case "flood-stderr" -> flood(System.err);
            case "echo-stdin" -> {
                System.out.write(System.in.readAllBytes());
                System.out.flush();
            }
            case "environment" -> {
                String name = arguments.length > 1 ? arguments[1] : "";
                System.out.print(System.getenv(name));
                System.out.flush();
            }
            case "write-file" -> {
                Path path = Path.of(arguments[1]);
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.writeString(path, arguments[2], StandardCharsets.UTF_8);
            }
            case "copy-stdin-to-file" -> {
                Path path = Path.of(arguments[1]);
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.write(path, System.in.readAllBytes());
            }
            default -> System.exit(2);
        }
    }

    private static void flood(java.io.PrintStream stream) {
        byte[] bytes = "x".repeat(1024).getBytes(StandardCharsets.UTF_8);
        while (true) {
            stream.write(bytes, 0, bytes.length);
            stream.flush();
        }
    }
}
