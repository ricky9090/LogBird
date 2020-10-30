package com.ricky9090.logbird;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Connection;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpHeaders;
import okio.Buffer;
import okio.BufferedSource;

/**
 * 修改自 HttpLoggingInterceptor，将Log发送至服务端
 * @see okhttp3.logging.HttpLoggingInterceptor
 */
public class LogBirdInteceptor implements Interceptor {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private boolean enableLog = false;
    private LogHandler logHandler;

    public LogBirdInteceptor(boolean logable, LogHandler logHandler) {
        this.enableLog = logable;
        this.logHandler = logHandler;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {

        Request request = chain.request();
        if (!enableLog) {
            return chain.proceed(request);
        }

        final TextLogger logger = new TextLogger();
        logger.attachLog();

        boolean logBody = true;
        boolean logHeaders = true;

        RequestBody requestBody = request.body();
        boolean hasRequestBody = requestBody != null;

        Connection connection = chain.connection();
        Protocol protocol = connection != null ? connection.protocol() : Protocol.HTTP_1_1;
        logger.log("<h3>Request</h3>");
        String requestStartMessage = "<b>--> " + request.method() + "</b> " + request.url() + " " + protocol;
        if (!logHeaders && hasRequestBody) {
            requestStartMessage += " (" + requestBody.contentLength() + "-byte body)";
        }
        logger.log(requestStartMessage);

        if (logHeaders) {
            if (hasRequestBody) {
                // Request body headers are only present when installed as a network interceptor. Force
                // them to be included (when available) so there values are known.
                if (requestBody.contentType() != null) {
                    logger.log("<b>Content-Type: </b>" + requestBody.contentType());
                }
                if (requestBody.contentLength() != -1) {
                    logger.log("<b>Content-Length: </b>" + requestBody.contentLength());
                }
            }

            Headers headers = request.headers();
            for (int i = 0, count = headers.size(); i < count; i++) {
                String name = headers.name(i);
                // Skip headers from the request body as they are explicitly logged above.
                if (!"Content-Type".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name)) {
                    logger.log("<b>" + name + ": </b>" + headers.value(i));
                }
            }

            if (!logBody || !hasRequestBody) {
                logger.log("<b>--> END " + request.method() + "</b>");
            } else if (bodyEncoded(request.headers())) {
                logger.log("<b>--> END " + request.method() + "</b> (encoded body omitted)");
            } else {
                Buffer buffer = new Buffer();
                requestBody.writeTo(buffer);

                Charset charset = UTF8;
                MediaType contentType = requestBody.contentType();
                if (contentType != null) {
                    charset = contentType.charset(UTF8);
                }

                logger.log("");
                if (isPlaintext(buffer)) {
                    logger.log(buffer.readString(charset));
                    logger.log("<b>--> END " + request.method()
                            + "</b> (" + requestBody.contentLength() + "-byte body)");
                } else {
                    logger.log("<b>--> END " + request.method() + "</b> (binary "
                            + requestBody.contentLength() + "-byte body omitted)");
                }
            }
        }

        long startNs = System.nanoTime();
        Response response;
        logger.log("");
        logger.log("<h3>Response</h3>");
        try {
            response = chain.proceed(request);
        } catch (Exception e) {
            logger.log("<b><-- HTTP FAILED:</b> " + e);
            throw e;
        }
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        ResponseBody responseBody = response.body();
        long contentLength = responseBody.contentLength();
        String bodySize = contentLength != -1 ? contentLength + "-byte" : "unknown-length";
        logger.log("<b><-- " + response.code() + "</b> " + response.message() + " "
                + response.request().url() + " (" + tookMs + "ms" + (!logHeaders ? ", "
                + bodySize + " body" : "") + ')');

        if (logHeaders) {
            Headers headers = response.headers();
            for (int i = 0, count = headers.size(); i < count; i++) {
                logger.log("<b>" + headers.name(i) + ": </b>" + headers.value(i));
            }

            if (!logBody || !HttpHeaders.hasBody(response)) {
                logger.log("<b><-- END HTTP</b>");
            } else if (bodyEncoded(response.headers())) {
                logger.log("<b><-- END HTTP (encoded body omitted)</b>");
            } else {
                BufferedSource source = responseBody.source();
                source.request(Long.MAX_VALUE); // Buffer the entire body.
                Buffer buffer = source.buffer();

                Charset charset = UTF8;
                MediaType contentType = responseBody.contentType();
                if (contentType != null) {
                    charset = contentType.charset(UTF8);
                }

                if (!isPlaintext(buffer)) {
                    logger.log("");
                    logger.log("<b><-- END HTTP</b> (binary " + buffer.size() + "-byte body omitted)");
                    return response;
                }

                if (contentLength != 0) {
                    logger.log("");
                    logger.log(buffer.clone().readString(charset));
                }

                logger.log("<b><-- END HTTP</b> (" + buffer.size() + "-byte body)");
            }
        }

        if (logHandler != null) {
            logHandler.handleLog(logger.getLog());
        }

        return response;
    }

    boolean isPlaintext(Buffer buffer) {
        try {
            Buffer prefix = new Buffer();
            long byteCount = buffer.size() < 64 ? buffer.size() : 64;
            buffer.copyTo(prefix, 0, byteCount);
            for (int i = 0; i < 16; i++) {
                if (prefix.exhausted()) {
                    break;
                }
                int codePoint = prefix.readUtf8CodePoint();
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false;
                }
            }
            return true;
        } catch (EOFException e) {
            return false; // Truncated UTF-8 sequence.
        }
    }

    private boolean bodyEncoded(Headers headers) {
        String contentEncoding = headers.get("Content-Encoding");
        return contentEncoding != null && !contentEncoding.equalsIgnoreCase("identity");
    }


    static class TextLogger {
        private static final String TAG_BR = "<br>";
        private StringBuilder stringBuilder = null;

        public void attachLog() {
            stringBuilder = new StringBuilder();
        }

        public void log(String msg) {
            stringBuilder.append(msg);
            stringBuilder.append(TAG_BR);
        }

        public String getLog() {
            if (stringBuilder != null) {
                return stringBuilder.toString();
            }
            return "";
        }
    }

    public static class LogHandlerImpl implements LogHandler {
        private final OkHttpClient logHttpClient;
        private final ExecutorService pool = Executors.newSingleThreadExecutor();

        public LogHandlerImpl() {
            logHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(5000, TimeUnit.MILLISECONDS)
                    .readTimeout(5000, TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(true).build();
        }

        @Override
        public void handleLog(final String log) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String logTag = Configuration.getInstance().getLogTag();
                        String logServer = Configuration.getInstance().getLogBirdServer();

                        RequestBody formBody = new FormBody.Builder()
                                .add("tag", logTag)
                                .add("log", log)
                                .build();
                        Request request = new Request.Builder()
                                .url(logServer)
                                .post(formBody)
                                .build();

                        Response response = logHttpClient.newCall(request).execute();
                        if (!response.isSuccessful())
                            throw new IOException("Unexpected code " + response);

                        System.out.println(response.body().string());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        }
    }

    interface LogHandler {
        void handleLog(String log);
    }
}



