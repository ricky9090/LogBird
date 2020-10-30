package com.ricky9090.logbirddemo;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.ricky9090.logbird.Configuration;
import com.ricky9090.logbird.LogBirdInteceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private TextView testButton;
    private TextView resultText;

    private OkHttpClient okHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initOkHttp();

        testButton = findViewById(R.id.test_btn);
        resultText = findViewById(R.id.result);

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestApi();
            }
        });
    }

    private void initOkHttp() {
        Interceptor loggingInterceptor = new LogBirdInteceptor(BuildConfig.DEBUG, new LogBirdInteceptor.LogHandlerImpl());

        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(5000, TimeUnit.MILLISECONDS)
                .readTimeout(5000, TimeUnit.MILLISECONDS)
                .addInterceptor(loggingInterceptor)  // log拦截器
                .retryOnConnectionFailure(true).build();

        // TODO 设置日志Tag
        Configuration.getInstance().setLogTag("LogBirdDemo");
        // TODO 设置日志上报服务端url
        Configuration.getInstance().setLogBirdServer("http://192.168.0.30:3000/uploadlog");
    }

    private void requestApi() {
        Request request = new Request.Builder()
                .url("https://www.wanandroid.com/article/list/0/json")
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                resultText.setText(response.body().string());
            }
        });
    }
}