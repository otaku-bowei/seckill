package com.example.seckill.constant;

public enum LogCode {
    C001("C001", "seckill request={}", LogLevel.INFO),
    S001("S001", "seckill user_id={} sku_id={}", LogLevel.INFO),
    S002("S002", "seckill success user_id={}", LogLevel.INFO),
    S003("S003", "seckill no_stock sku_id={}", LogLevel.WARN),
    S004("S004", "seckill already_bought user_id={}", LogLevel.WARN),
    E001("E001", "seckill_error user_id={}", LogLevel.ERROR);

    private final String code;
    private final String template;
    private final LogLevel level;

    LogCode(String code, String template, LogLevel level) {
        this.code = code;
        this.template = template;
        this.level = level;
    }

    public String getCode() { return code; }
    public String getTemplate() { return template; }
    public LogLevel getLevel() { return level; }
}