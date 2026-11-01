package com.carepilot.chat.ws;

/**
 * WS 帧类型常量。前后端协议，改名会同时影响前端解析。
 */
public final class WsFrameType {
    public static final String READY = "READY";
    public static final String ACK = "ACK";
    public static final String AI_DELTA = "AI_DELTA";
    public static final String AI_DONE = "AI_DONE";
    public static final String TOOL_CALL = "TOOL_CALL";
    public static final String APPROVAL = "APPROVAL";
    public static final String APPROVAL_RESULT = "APPROVAL_RESULT";
    public static final String QUEUE_STATUS = "QUEUE_STATUS";
    public static final String QUEUE_CANCELED = "QUEUE_CANCELED";
    public static final String AGENT_JOINED = "AGENT_JOINED";
    public static final String AGENT_MESSAGE = "AGENT_MESSAGE";
    public static final String SESSION_CLOSED = "SESSION_CLOSED";
    public static final String SESSION_REOPENED = "SESSION_REOPENED";
    public static final String SESSION_SUMMARY = "SESSION_SUMMARY";

    public static final String QUEUE_CHANGED = "QUEUE_CHANGED";

    public static final String AGENT_LEFT = "AGENT_LEFT";
    public static final String PONG = "PONG";
    public static final String ERROR = "ERROR";

    private WsFrameType() {}
}