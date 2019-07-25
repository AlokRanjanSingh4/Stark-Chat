package singh.alok.starkchatproject;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Callback;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.theartofdev.edmodo.cropper.CropImage;

import id.zelory.compressor.Compressor;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;


public class SettingsActivity extends AppCompatActivity {

    private ImageView mDisplayImage;
    private TextView mDisplayUserName;
    private TextView mUserStatus;
    private Button mImageBtn;
    private Button mStatusBtn;

    private FirebaseUser mCurrentUser;

    private DatabaseReference mUserDatabase;

    private StorageReference mFirebaseStorage;
    private ProgressDialog mProgressDialog;


    private final static int GALLERY_PICK = 1;


    private String uid;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mDisplayImage = findViewById(R.id.settings_image);
        mDisplayUserName = findViewById(R.id.setting_display_name);
        mUserStatus = findViewById(R.id.settings_status);
        mStatusBtn = findViewById(R.id.settings_status_btn);
        mImageBtn = findViewById(R.id.settings_image_btn);

        mFirebaseStorage = FirebaseStorage.getInstance().getReference();

        mCurrentUser = FirebaseAuth.getInstance().getCurrentUser();
        uid = mCurrentUser.getUid();

        mUserDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child(uid);

        mUserDatabase.keepSynced(true);

        mUserDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                String name = dataSnapshot.child("name").getValue().toString();
                final String image = dataSnapshot.child("image").getValue().toString();
                String status = dataSnapshot.child("status").getValue().toString();
                String thumb_image = dataSnapshot.child("thumb_image").getValue().toString();
                Log.d("TAG", "image = " + image);

                mDisplayUserName.setText(name);
                mUserStatus.setText(status);

                if (!image.equals("default")) {

//                    Picasso.get().load(image).placeholder(R.drawable.assets).into(mDisplayImage);
                    Picasso.get().load(image).networkPolicy(NetworkPolicy.OFFLINE).into(mDisplayImage, new Callback() {
                        @Override
                        public void onSuccess() {

                        }

                        @Override
                        public void onError(Exception e) {

                            Picasso.get().load(image).into(mDisplayImage);

                        }
                    });

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        mStatusBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                String status_value = mUserStatus.getText().toString();

                Intent statusIntent = new Intent(SettingsActivity.this, StatusActivity.class);
                statusIntent.putExtra("status_value", status_value);
                startActivity(statusIntent);

            }
        });

        mImageBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Intent galleryIntent = new Intent();
                galleryIntent.setType("image/*");
                galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(galleryIntent, "SELECT IMAGE"), GALLERY_PICK);

            }
        });

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GALLERY_PICK && resultCode == RESULT_OK) {

            //show the progressdiaglog to let user wait a little bit
            mProgressDialog = new ProgressDialog(SettingsActivity.this);
            mProgressDialog.setTitle("Uploading Image...");
            mProgressDialog.setMessage("Please wait while we upload and process the image.");
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.show();

            Uri imageUri = data.getData();
            CropImage.activity(imageUri)
                    .setAspectRatio(1, 1)
                    .start(this);

        }

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {

            CropImage.ActivityResult result = CropImage.getActivityResult(data);

            if (resultCode == RESULT_OK) {

                Uri resultUri = result.getUri();

//              Uploading thumb image with bitmap format to firebase storage
//              Create File object for uri of cropped image
                File thumb_file_path = new File(resultUri.getPath());

                try {
//                  Custom the compressor to compress the image
                    Bitmap thumb_bitmap = new Compressor(this)
                            .setMaxWidth(200)
                            .setMaxHeight(200)
                            .setQuality(75)
                            .compressToBitmap(thumb_file_path);

                    ByteArrayOutputStream thumbByteArrayOutputStream = new ByteArrayOutputStream();

//                  Compress the above thumb_bitmap to ByteArrayOutputStream
                    thumb_bitmap.compress(Bitmap.CompressFormat.JPEG, 100, thumbByteArrayOutputStream);
                    byte[] thumb_byte_data = thumbByteArrayOutputStream.toByteArray();

//                  Store thumb to firebase storage with path: profile_images -> thumbs -> namefile.jpg
                    final StorageReference thumb_path = mFirebaseStorage.child("profile_images").child("thumbs").child(uid + ".jpg ");

//                  Using uploadTask to upload bitmap format to firebase storage
                    UploadTask uploadTask = thumb_path.putBytes(thumb_byte_data);
                    uploadTask.addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {

                        @Override
                        public void onComplete(Task<UploadTask.TaskSnapshot> thumb_task) {

                            if (thumb_task.isSuccessful()) {

//                                Toast.makeText(SettingsActivity.this, "Uploaded thumb image successfully", Toast.LENGTH_SHORT).show();
                                thumb_path.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {

                                    @Override
                                    public void onSuccess(Uri uri) {

                                        String thumb_download_uri = uri.toString();
                                        mUserDatabase.child("thumb_image").setValue(thumb_download_uri);

                                    }
                                });

                            } else {

                                Toast.makeText(SettingsActivity.this, "Uploaded thumb image failed", Toast.LENGTH_SHORT).show();

                            }

                        }
                    });


//                  Upload directly with original image format
//                  Named the original image file by uid
                    final StorageReference filepath = mFirebaseStorage.child("profile_images").child(uid + ".jpg");

//                  Put image file to firebase storage
                    filepath.putFile(resultUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {

                        @Override
                        public void onComplete(Task<UploadTask.TaskSnapshot> task) {

                            if (task.isSuccessful()) {

                                Toast.makeText(SettingsActivity.this, "Upload image successfully", Toast.LENGTH_SHORT).show();
                                filepath.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {

                                    @Override
                                    public void onSuccess(Uri uri) {

                                        String download_uri = uri.toString();
                                        mUserDatabase.child("image").setValue(download_uri);
                                        mProgressDialog.dismiss();
                                        Log.d("TAG", download_uri);

                                    }
                                });

                            } else {

                                mProgressDialog.hide();
                                Toast.makeText(SettingsActivity.this, "Upload image failed", Toast.LENGTH_SHORT).show();

                            }
                        }
                    });

                } catch (IOException e) {

                    e.printStackTrace();

                }

            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {

                Exception error = result.getError();

            }
        }
    }
}
