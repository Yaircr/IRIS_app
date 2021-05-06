package com.iris.fotoapparatapi;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;

import java.io.FileNotFoundException;
import java.util.ArrayList;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class activity_Procesando extends AppCompatActivity {


    private Context ctx = null;
    private ArrayList<Bitmap> stack = new ArrayList<>();
    private ImagePackager mPackager;
    private Disposable mDisposable;
    private ArrayList<String> bmps = new ArrayList<>();
    private ProgressBar pb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity__procesando);
        ctx = getApplicationContext();
        pb = findViewById(R.id.progressBar);
        pb.setVisibility(View.INVISIBLE);
        doSetUp();
        //Obtener bitmaps
        bmps = (ArrayList<String>) getIntent().getSerializableExtra("bitmaps");
        contarBmps();
        doSetUp();
        doImageProcessing();
    }
    public void addToStack(Bitmap bmp){
        stack.add(bmp);
    }

    private int getImageCount(){
        return stack.size();
    }

    public void doSetUp(){
        mPackager = new ImagePackager(this.getImageCount(),stack, bmps);
    }

    public void doImageProcessing(){
        BloquearInteraccion();
        mDisposable = mPackager.procesar()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::ProcesamientoTerminado);
    }

    private void BloquearInteraccion() {
        pb.setVisibility(View.VISIBLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void PermitirInteraccion() {
        pb.setVisibility(View.INVISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void ProcesamientoTerminado(ArrayList<ProcessedPackage> result) {
        PermitirInteraccion();
        //TODO: regresar conteo de imagenes procesadas y cambiar visibilidad del progreso.

    }

    private Bitmap recuperarBitmap(String name){
        Bitmap bmp = null;
        try {
            bmp = BitmapFactory.decodeStream(ctx.openFileInput(name));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return bmp;
    }
    private void contarBmps(){
        if(bmps.size()!=0) {

            Log.d("ACTIVITY_PROCESANDO","Se recibieron "+bmps.size()+" bitmaps");
            for (int i = 0; i < bmps.size(); i++) {
                Log.d("ACTIVITY_PROCESANDO", "Nombre del Bitmap["+i+"]="+bmps.get(i));
                Bitmap x = recuperarBitmap(bmps.get(i));
                if(x != null){
                    Log.d("ACTIVITY_PROCESANDO", "TAMAÑO DEL BITMAP["+i+"]"+x.getByteCount());
                    addToStack(x);
                }
            }
        }else{
            Log.d("ACTIVITY_PROCESANDO","No se recibieron bitmaps");
        }
    }
}