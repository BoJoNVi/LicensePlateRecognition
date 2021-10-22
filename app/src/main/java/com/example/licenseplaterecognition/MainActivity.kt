package com.example.licenseplaterecognition

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.licenseplaterecognition.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding;
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        outputDirectory = getOutputDirectory()
        if (allPermissionGranted()) {
            cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener(Runnable {
                val cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            }, ContextCompat.getMainExecutor(this))
        } else {
            ActivityCompat.requestPermissions(
                this,
                Constants.REQUIRED_PERMISSION,
                Constants.REQUEST_CODE_PERMISSIONS
            )
        }

        val date = Calendar.getInstance()
        val day = date.getDisplayName(Calendar.DAY_OF_WEEK, 1, Locale("en", "PH", "PH"))
        binding.textView3.text = "Day: $day"
        binding.button.setOnClickListener {
            Toast.makeText(this@MainActivity, "Capture", Toast.LENGTH_SHORT).show()
            takePhoto("asd")
        }
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder()
            .build()

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider())

        val date = Calendar.getInstance()
        val day = date.get(Calendar.DAY_OF_WEEK)
        val lastDigitBan = hashMapOf(1 to 2,
            2 to 2,
            3 to 3,
            4 to 3,
            5 to 4,
            6 to 4,
            7 to 5,
            8 to 5,
            9 to 6,
            0 to 6)
        val initial: MutableList<String> = mutableListOf()
        val exist: MutableList<String> = mutableListOf()
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageCapture = ImageCapture.Builder()
            .build()
        imageAnalysis.setAnalyzer(
            Executors.newSingleThreadExecutor(),
            ImageAnalysis.Analyzer { image ->
                val rotationDegrees = image.imageInfo.rotationDegrees
                // Initialize Text Recognition
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                // Initialize Object Detection
                val localModel = LocalModel.Builder()
                    .setAssetFilePath("model.tflite")
                    .build()
                val customObjectDetectorOptions =
                    CustomObjectDetectorOptions.Builder(localModel)
                        .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                        .enableMultipleObjects()
                        .enableClassification()
                        .setClassificationConfidenceThreshold(0.8f)
                        .setMaxPerObjectLabelCount(3)
                        .build()
                val objectDetector = ObjectDetection.getClient(customObjectDetectorOptions)

                // Image Processing
                @androidx.camera.core.ExperimentalGetImage
                val mediaImage = image.image
                @androidx.camera.core.ExperimentalGetImage
                if (mediaImage != null) {
                    val image1 = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                    // Object Processing
                    objectDetector.process(image1)
                        .addOnSuccessListener { detectedObjects ->
                            if (detectedObjects.size == 0) {
                                binding.textView.text = "No Object Found"
                                binding.textView2.text = "Position: Not Found"
                            } else {
                                for (detectedObject in detectedObjects) {
//                                    val boundingBox = detectedObject.boundingBox
//                                    val trackingId = detectedObject.trackingId
                                    for (label in detectedObject.labels) {
                                        val text = label.text
//                                        val confidence = label.confidence
                                        binding.textView.text = "$text"
//                                        binding.textView2.text = "Position: $boundingBox"
                                    }
                                }
                            }
                            if (binding.textView.text != "license_plate") {
                                image.close()
                            } else {
                                recognizer.process(image1)
                                    .addOnSuccessListener { visionText ->
                                        for (block in visionText.textBlocks) {
//                                            binding.textView2.text =
//                                                "Block: ${block.text}"
                                            for (line in block.lines) {
                                                val lineText = line.text.replace(
                                                    "\\s".toRegex(),
                                                    ""
                                                )
                                                if (isPlateNumber(lineText)) {
                                                    // Double check the recognized text
                                                    if (initial.contains(lineText)) {
                                                        val lastDigit =
                                                            lineText.last().digitToInt()
                                                        binding.textView2.text =
                                                            "Text: $lineText"
                                                        // Check if it's already recognized or not
                                                        if (!exist.contains(lineText)) {
                                                            exist.add(lineText)
                                                            if (lastDigitBan[lastDigit] == day) {
                                                                takePhoto(lineText)
                                                            }
                                                            break
                                                        }
                                                    } else {
                                                        initial.add(lineText)
                                                    }
                                                }
                                            }
                                        }
                                        image.close()
                                    }
                                    .addOnFailureListener { e ->
                                        binding.textView2.text =
                                            e.toString()
                                        image.close()
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            binding.textView2.text =
                                "Error"
                            image.close()
                        }
                } else {
                    image.close()
                }
            })
        var camera = cameraProvider.bindToLifecycle(
            this as LifecycleOwner, cameraSelector,
            imageCapture,
            imageAnalysis,
            preview
        )
    }

    private fun String.onlyLetters() = all { it.isLetter() }
    private fun String.onlyDigits() = all { it.isDigit() }
    private fun isPlateNumber(lineText: String): Boolean {
        if (lineText.length == 6) {
            return (lineText.take(3).onlyLetters() && lineText.takeLast(3).onlyDigits())
        } else if (lineText.length == 7) {
            return (lineText.take(3).onlyLetters() && lineText.takeLast(4).onlyDigits())
        }
        return false
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let { mFile ->
            File(mFile, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun takePhoto(lineText: String) {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,
            "$lineText - " +
                    SimpleDateFormat(
                        Constants.FILE_NAME_FORMAT,
                        Locale.getDefault()
                    ).format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOption = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOption,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo Saved!"

                    Toast.makeText(this@MainActivity, "$msg $savedUri", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(Constants.TAG, "error ${exception.message}", exception)
                }
            }
        )
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
            val f = photoFile
            mediaScanIntent.data = Uri.fromFile(f)
            sendBroadcast(mediaScanIntent)
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {

        if (requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()) {
                cameraProviderFuture = ProcessCameraProvider.getInstance(this)
                cameraProviderFuture.addListener(Runnable {
                    val cameraProvider = cameraProviderFuture.get()
                    bindPreview(cameraProvider)
                }, ContextCompat.getMainExecutor(this))
            } else {
                Toast.makeText(this, "hello", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun allPermissionGranted() =
        Constants.REQUIRED_PERMISSION.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }

}