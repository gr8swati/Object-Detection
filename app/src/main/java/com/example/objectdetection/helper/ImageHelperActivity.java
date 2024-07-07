package com.example.objectdetection.helper;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.objectdetection.R;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ImageHelperActivity extends AppCompatActivity {
    private final int REQUEST_CODE_FOR_PICK_IMG = 1000;
    private final int REQUEST_CODE_FOR_CAPTURE_IMG = 1001;
    ImageLabeler imageLabeler;
    ImageView pickImg;
    protected TextView output;
    private File photoFile;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_image_helper);
        pickImg = findViewById(R.id.img);
        output = findViewById(R.id.output);
        imageLabeler = ImageLabeling.getClient(new ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.7f).build());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;

        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)!= PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 0);
            }
        }
    }
    public void onPickImage(View view){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent,REQUEST_CODE_FOR_PICK_IMG);
    }
    public void onStartCamera(View view){
//         create a file to share with camera
        photoFile = createPhotoFile();
        Uri fileUri = FileProvider.getUriForFile(this, "com.iago.fileprovider" , photoFile);

//        create an intent
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,fileUri);

//        startActivityForResult
          startActivityForResult(intent , REQUEST_CODE_FOR_CAPTURE_IMG);

    }

    private File createPhotoFile(){
      File photoFileDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ML_IMAGE_HELPER");
      if (!photoFileDir.exists()){photoFileDir.mkdir();
      }
      String name = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
      File file = new File(photoFileDir.getPath() + File.separator + name);
      return file;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK){
            if (requestCode == REQUEST_CODE_FOR_PICK_IMG){
                Uri uri = data.getData();
                Bitmap bitmap = loadFromUri(uri);
                pickImg.setImageBitmap(bitmap);
                runClassification(bitmap);
            } else if (requestCode == REQUEST_CODE_FOR_CAPTURE_IMG) {
                Log.d("ML","image recieved.");
                Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                pickImg.setImageBitmap(bitmap);
                runClassification(bitmap);

            }
        }
    }
    private Bitmap loadFromUri(Uri uri){
        Bitmap bitmap = null;
       try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                bitmap = ImageDecoder.decodeBitmap(source);
            } else {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver() , uri);
            }
        }catch (IOException e){
           e.printStackTrace();
       }
        return bitmap;
    }
    protected void runClassification(Bitmap bitmap){
        InputImage inputImage = InputImage.fromBitmap(bitmap ,0);
        imageLabeler.process(inputImage).addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
            @Override
            public void onSuccess(List<ImageLabel> imageLabels) {
                 if (imageLabels.size()>0){
                     StringBuilder builder = new StringBuilder();
                     for (ImageLabel label : imageLabels){
                         builder.append(label.getText()).
                                 append(" : ").append(label.getConfidence())
                                 .append("\n");
                     }
                     output.setText(builder.toString());
                 }else{
                     output.setText("Could not classify");
                 }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
              e.printStackTrace();
            }
        });
    }

    protected TextView getOutput(){
        return output;
    }
    protected ImageView getInputImage(){
        return pickImg;
    }
    protected void drawBoxWithLabel(List<BoxWithLabel> boxes , Bitmap bitmap){
        Bitmap bitmap1 = bitmap.copy(Bitmap.Config.ARGB_8888 , true);
        Canvas canvas = new Canvas(bitmap1);
        Paint pRect = new Paint();
        pRect.setColor(Color.RED);
        pRect.setStyle(Paint.Style.STROKE);
        pRect.setStrokeWidth(8f);



        Paint pLabel = new Paint();
        pLabel.setColor(Color.YELLOW);
        pLabel.setStyle(Paint.Style.FILL_AND_STROKE);
        pLabel.setTextSize(96f);

        for (BoxWithLabel boxWithLabel : boxes){
          canvas.drawRect(boxWithLabel.rect , pRect);

          Rect labelSize = new Rect(0,0,0,0);
          pLabel.getTextBounds(boxWithLabel.label, 0 , boxWithLabel.label.length(), labelSize);

          float fontSize = pLabel.getTextSize() * boxWithLabel.rect.width() / labelSize.width();

          if (fontSize < pLabel.getTextSize()){
              pLabel.setTextSize(fontSize);
          }
          canvas.drawText(boxWithLabel.label , boxWithLabel.rect.left , labelSize.height() , pLabel);
        }
        getInputImage().setImageBitmap(bitmap1);
    }
}