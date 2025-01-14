package com.iris.photocapture;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.iris.photocapture.threading.ImagePackager;
import com.iris.photocapture.threading.ProcessedPackage;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class activity_Procesando extends AppCompatActivity implements RecyclerViewAdapter.ItemClickListener {


    private Context ctx = null;
    private ArrayList<Bitmap> stack = new ArrayList<>();
    private ImagePackager mPackager;
    private Disposable mDisposable;
    private ArrayList<String> bmps = new ArrayList<>();
    private ProgressBar pb;
    private ArrayList<ProcessedPackage> mPostProcessing = new ArrayList<>();
    private RecyclerViewAdapter adapter;
    private RecyclerView recyclerView;
    private EditText nombre;
    private Button comenzar;
    private Bitmap aux;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity__procesando);
        ctx = getApplicationContext();
        pb = findViewById(R.id.progressBar);
        pb.setVisibility(View.INVISIBLE);

        comenzar = (Button) findViewById(R.id.Guardaryprocesar);
        nombre = (EditText) findViewById(R.id.EditNombreSesion);
        comenzar.setOnClickListener(v -> nombrarSesion());
        // set up the RecyclerView
        recyclerView = findViewById(R.id.RecyclerImagenes);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new RecyclerViewAdapter(this, mPostProcessing);
        adapter.setClickListener(this::onItemClick);
        recyclerView.setAdapter(adapter);

    }

    @Override
    public void onItemClick(View view, int position) {
        try {
            //Log.i("ACTIVITY_PROCESANDO RECYCLER-ITEMCLICK", "You clicked " + adapter.getItem(position).getName() + ", which is at cell position " + position);
            Intent i = new Intent(ctx, PostProcessingDetailedView.class);
            i.putExtra("PostProcessed", adapter.getItem(position));
            startActivity(i);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void addToStack(Bitmap bmp){
        stack.add(bmp);
    }

    private int getImageCount(){
        return stack.size();
    }

    public void doSetUp(){
        String sesion = nombre.getText().toString();
        mPackager = new ImagePackager(this.getImageCount(),stack, bmps,ctx,sesion);
        //Log.d("ACTIVITY_PROCESANDO DOSETUP","NOMBRE DE SESION=>"+nombre.getText().toString());

    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
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
        mPostProcessing = result;
        //Log.d("ACTIVITY_PROCESANDO","NOMBRE DE LA SESION=>"+mPostProcessing.get(0).getmSessionName());
        mPostProcessing.sort(Comparator.comparing(ProcessedPackage::getId));
        recyclerView.setAdapter(null);
        recyclerView.setLayoutManager(null);
        adapter = new RecyclerViewAdapter(this, mPostProcessing);
        adapter.setClickListener(this::onItemClick);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter.notifyDataSetChanged();

        PermitirInteraccion();
        for(int i=0;i<result.size();i++){
            ProcessedPackage pp = result.get(i);
            String name = pp.getName();
            long time = pp.getTimeTaken();
            int id = pp.getId();
            int umbral = pp.getmThreshold();
            Log.d("ACTIVITY_PROCESANDO","Tiempo de procesamiento para el Bitmap["+name+"] ID=["+id+"] Umbral de Otsu["+umbral+"]=> "+ time);
        }
        Toast.makeText(ctx,"PROCESAMIENTO DE "+result.size()+" fotos Terminado",Toast.LENGTH_LONG).show();
        if(mDisposable != null){
            mDisposable.dispose();
        }

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
                //Log.d("ACTIVITY_PROCESANDO", "Nombre del Bitmap["+i+"]="+bmps.get(i));
                try {
                    aux =  BitmapFactory.decodeStream(ctx.openFileInput(bmps.get(i)));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                if(aux != null){
                    //Log.d("ACTIVITY_PROCESANDO", "TAMAÑO DEL BITMAP["+i+"]"+x.getByteCount());
                    addToStack(aux);
                    java.lang.System.gc();
                }
            }

        }else{
            Log.d("ACTIVITY_PROCESANDO","No se recibieron bitmaps");
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void nombrarSesion(){
        if(!nombre.getText().toString().isEmpty()) {
            Toast.makeText(ctx,"Comenzando procesamiento de las imagenes en hilos", Toast.LENGTH_LONG).show();
            comenzar.setEnabled(false);
            nombre.setEnabled(false);
            doSetUp();
            //Obtener bitmaps
            bmps = (ArrayList<String>) getIntent().getSerializableExtra("bitmaps");
            contarBmps();
            doSetUp();
            doImageProcessing();
        }else{
            Toast.makeText(ctx,"Se requiere un nombre para la sesion antes de comenzar el guardado",Toast.LENGTH_LONG).show();
        }
    }
}