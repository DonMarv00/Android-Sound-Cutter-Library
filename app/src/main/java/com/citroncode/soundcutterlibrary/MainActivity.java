package com.citroncode.soundcutterlibrary;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.citroncode.soundcutter.CutterView;
import com.citroncode.soundcutterlibrary.databinding.ActivityMainBinding;

import java.util.concurrent.atomic.AtomicReference;


public class MainActivity extends AppCompatActivity  {

    ActivityMainBinding binding;
    Button btn_save, btn_load;
    CutterView cutterView;
    int REQ_CODE_EXTERNAL_STORAGE_PERMISSION = 23;
    int last_action = 3;
    Boolean wasSaved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        btn_save = binding.btnSave;
        btn_load = binding.btnLoad;
        cutterView = binding.cutterview;

        btn_save.setOnClickListener(v -> {
            last_action = 0;
            saveCuttedFile();
        });
        btn_load.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                last_action = 1;
                selectAudioFile();
            } else {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_CODE_EXTERNAL_STORAGE_PERMISSION);
            }
        });

    }
    private void selectAudioFile(){
        Intent intent = new Intent();
        intent.setType("audio/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        someActivityResultLauncher.launch(intent);
    }
    private void saveCuttedFile(){
        LayoutInflater inflater = getLayoutInflater();
        final View view = inflater.inflate(R.layout.dialog_save_audio,null);
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Create a Soundboard");
        alertDialog.setCancelable(false);
        final EditText et_name = (EditText) view.findViewById(R.id.et_name);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.save), (dialog, which) -> {
                cutterView.saveSound(et_name.getText().toString());
        });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel), (dialog, which) -> alertDialog.dismiss());
        alertDialog.setView(view);
        alertDialog.show();
    }
    ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    assert data != null;
                    Uri uri = data.getData();

                    if (uri != null) {
                        cutterView.setSound(uri,this,MainActivity.this);
                    }else{
                        Toast.makeText(this, getResources().getString(R.string.error) + "Returned Uri is null", Toast.LENGTH_SHORT).show();
                    }

                }});

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQ_CODE_EXTERNAL_STORAGE_PERMISSION && grantResults.length >0 &&grantResults[0] == PackageManager.PERMISSION_GRANTED){
            if(last_action == 0){
                saveCuttedFile();
            }else{
                selectAudioFile();
            }
        }
    }
}