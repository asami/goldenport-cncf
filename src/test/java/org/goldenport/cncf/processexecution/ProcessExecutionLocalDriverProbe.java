package org.goldenport.cncf.processexecution;

import java.nio.charset.StandardCharsets;

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
