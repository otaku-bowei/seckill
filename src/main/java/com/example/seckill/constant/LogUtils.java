package com.example.seckill.constant;

public final class LogUtils {

    private LogUtils() {
    }

    public static void log(LogCode code, Object... args) {
        String template = code.getTemplate();
        String msg = template;
        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }

        switch (code.getLevel()) {
            case DEBUG:
                System.out.println("[DEBUG] " + msg);
                break;
            case INFO:
                System.out.println("[INFO] " + msg);
                break;
            case WARN:
                System.out.println("[WARN] " + msg);
                break;
            case ERROR:
                System.err.println("[ERROR] " + msg);
                break;
        }
    }
}