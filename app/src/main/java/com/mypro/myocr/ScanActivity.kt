package com.mypro.myocr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mypro.myocr.databinding.ActivityScanBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ScanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScanBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var recognizer: TextRecognizer
    private var capturedImage: InputImage? = null
    private var imageProxy: ImageProxy? = null
    private var croppedImage: Bitmap? = null
    private val loading by lazy { LoadingDialog(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_scan)

        requestPermission()
        initView()
    }

    private fun initView() {
        binding.apply {
            imageCaptureButton.setOnClickListener {
                takePhoto()
            }
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private fun requestPermission() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                }

            val viewPort = ViewPort.Builder(Rational(200, 100), Surface.ROTATION_0).build()
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageAnalyzer)
                .addUseCase(imageCapture!!)
                .setViewPort(viewPort)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { startCrop(it) }
                }
            }
        )

//        capturedImage?.let {
//            recognizer.process(it)
//                .addOnSuccessListener { visionText ->
//                    loading.dismiss()
//                    val intent = Intent()
//                    intent.putExtra(EXTRA_TEXT, visionText.text)
//                    setResult(Activity.RESULT_OK, intent)
//                    finish()
//                }
//                .addOnFailureListener {
//                    loading.dismiss()
//                    Toast.makeText(
//                        this@ScanActivity,
//                        "Failed to scan image",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//                .addOnCompleteListener {
//                    imageProxy?.close()
//                }
//        }
//
//
//        loading.show()
//        binding.img.setImageBitmap(
//            ImageConvertUtils.getInstance().getUpRightBitmap(capturedImage!!)
//        )
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    inner class ImageAnalyzer : ImageAnalysis.Analyzer {

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            this@ScanActivity.imageProxy = imageProxy
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                capturedImage =
                    InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )
//                val bmp = ImageConvertUtils.getInstance().getUpRightBitmap(capturedImage!!)
//                val croppedImage = cropImage(bmp, binding.viewFinder, binding.vReference)
//                capturedImage = InputImage.fromBitmap(croppedImage, 0)
            }
        }
    }


    private fun cropImage(bitmap: Bitmap, frame: View, reference: View): Bitmap {
        val heightOriginal = frame.height
        val widthOriginal = frame.width
        val heightFrame = reference.height
        val widthFrame = reference.width
        val leftFrame = reference.left
        val topFrame = reference.top
        val heightReal = bitmap.height
        val widthReal = bitmap.width
        val widthFinal = widthFrame * widthReal / widthOriginal
        val heightFinal = heightFrame * heightReal / heightOriginal
        val leftFinal = leftFrame * widthReal / widthOriginal
        val topFinal = topFrame * heightReal / heightOriginal
        val bitmapFinal = Bitmap.createBitmap(
            bitmap,
            leftFinal, topFinal, widthFinal, heightFinal
        )
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmapFinal,
            bitmapFinal.width * 3,
            bitmapFinal.height * 3,
            false
        )
//        val stream = ByteArrayOutputStream()
//        bitmapFinal.compress(
//            Bitmap.CompressFormat.JPEG,
//            100,
//            stream
//        ) //100 is the best quality possibe
        return scaledBitmap
    }

    fun Image.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            // use the returned uri
            val uriContent = result.uriContent
            val uriFilePath = result.getUriFilePath(this)

            val image = uriContent?.let { InputImage.fromFilePath(this, it) }
            image?.let {
                recognizer.process(it)
                    .addOnSuccessListener { visionText ->
                        loading.dismiss()

                        val re = Regex("[^A-Za-z]")
                        visionText.text.replace(re, "")

                        val intent = Intent()
                        intent.putExtra(EXTRA_TEXT, visionText.text)
                        setResult(Activity.RESULT_OK, intent)
                        finish()
                    }
                    .addOnFailureListener {
                        loading.dismiss()
                        Toast.makeText(
                            this@ScanActivity,
                            "Failed to scan image",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnCompleteListener {
                        imageProxy?.close()
                    }
            }
            loading.show()

        } else {
            // an error occurred
            val exception = result.error
        }
    }

    private fun startCrop(uri: Uri) {
        // start cropping activity for pre-acquired image saved on the device and customize settings
        cropImage.launch(
            options(uri = uri) {
                setGuidelines(CropImageView.Guidelines.ON)
                setOutputCompressFormat(Bitmap.CompressFormat.PNG)
            }
        )
    }


    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
        const val EXTRA_TEXT = "text"
    }
}