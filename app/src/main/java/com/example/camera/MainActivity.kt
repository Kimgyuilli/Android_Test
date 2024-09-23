package com.example.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var curPhotoPath: String // 사진 경로
    private lateinit var ivProfile: ImageView // 이미지 뷰
    private val galleryRequestCode = 2 // 갤러리 요청 코드

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 엣지 투 엣지 설정
        applyEdgeToEdge()

        ivProfile = findViewById(R.id.imageView) // 이미지 뷰 초기화
        val btnCamera = findViewById<Button>(R.id.btn_camera) // 카메라 버튼 연동
        // 카메라 버튼 클릭 리스너
        btnCamera.setOnClickListener { takeCapture() }

        val btnGallery = findViewById<Button>(R.id.btn_gallery) // 갤러리 버튼 연동
        // 갤러리 버튼 클릭 리스너
        btnGallery.setOnClickListener { openGallery() }

        // 권한 요청
        requestPermissions()
    }

    // 엣지 투 엣지 설정
    private fun applyEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // 권한 요청
    private fun requestPermissions() {
        val permissionListener = object : PermissionListener {
            override fun onPermissionGranted() {
                Toast.makeText(this@MainActivity, "Permission Granted", Toast.LENGTH_SHORT).show()
            }

            override fun onPermissionDenied(deniedPermissions: List<String?>) {
                Toast.makeText(this@MainActivity, "Permission Denied: $deniedPermissions", Toast.LENGTH_SHORT).show()
            }
        }

        TedPermission.create()
            .setPermissionListener(permissionListener)
            .setDeniedMessage("If you reject permission, you cannot use this service. Please enable permissions in Settings.")
            .setPermissions(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .check()
    }

    // 카메라 촬영 결과 처리
    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val bitmap = getCapturedImage()
                bitmap?.let {
                    ivProfile.setImageBitmap(it) // 촬영된 이미지를 ImageView에 표시
                    savePhoto(it) // 사진 저장
                }
            }
        }

    // 이미지 처리 함수
    private fun getCapturedImage(): Bitmap? {
        val file = File(curPhotoPath)
        return try {
            if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(contentResolver, Uri.fromFile(file))
            } else {
                val source = ImageDecoder.createSource(contentResolver, Uri.fromFile(file))
                ImageDecoder.decodeBitmap(source)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "사진을 불러오는 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // 카메라 실행
    @SuppressLint("QueryPermissionsNeeded")
    private fun takeCapture() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureIntent.resolveActivity(packageManager)?.let {
            val photoFile = try {
                createImageFile()
            } catch (ex: IOException) {
                Toast.makeText(this, "파일을 생성하는 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                null
            }

            photoFile?.let {
                val photoURI: Uri = FileProvider.getUriForFile(this, "com.example.camera.fileprovider", it)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startForResult.launch(takePictureIntent)
            }
        }
    }
    // 갤러리 열기
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, galleryRequestCode)
    }

    // 이미지 파일 생성
    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timestamp}_", ".jpg", storageDir).apply {
            curPhotoPath = absolutePath
        }
    }

    // 갤러리에서 사진 선택 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == galleryRequestCode && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val bitmap = loadImageFromUri(uri)
                bitmap?.let { ivProfile.setImageBitmap(it) }
            }
        }
    }

    // 갤러리에서 선택한 이미지 로드
    private fun loadImageFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            } else {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "이미지를 불러오는 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // 사진 저장
    private fun savePhoto(bitmap: Bitmap) {
        val filename = "${System.currentTimeMillis()}.jpeg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        imageUri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(it, contentValues, null, null)
            }
            Toast.makeText(this, "사진이 앨범에 저장되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}
