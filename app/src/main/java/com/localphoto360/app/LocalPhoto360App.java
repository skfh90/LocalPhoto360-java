package com.localphoto360.app;

import android.app.Application;
import android.content.Context;

import com.localphoto360.app.data.PhotoRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalPhoto360App extends Application {
    private PhotoRepository photos;
    private ExecutorService ioExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        photos = new PhotoRepository(this);
        ioExecutor = Executors.newCachedThreadPool();
    }

    public PhotoRepository photos() {
        return photos;
    }

    public ExecutorService io() {
        return ioExecutor;
    }

    public static LocalPhoto360App from(Context context) {
        return (LocalPhoto360App) context.getApplicationContext();
    }
}
