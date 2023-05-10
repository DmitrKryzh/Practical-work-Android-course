package com.example.m20_firebase


import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDeepLinkBuilder
import androidx.navigation.fragment.findNavController
import com.example.m20_firebase.databinding.CameraFragmentBinding
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.random.Random

private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss"
private const val DATE_FORMAT = "yyyy-MM-dd"

class CameraFragment : Fragment() {

    private var _binding: CameraFragmentBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private lateinit var executor: Executor


    private val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
        .format(System.currentTimeMillis())

    private val launcher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        if (map.values.all { it }) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "permission is not granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = CameraFragmentBinding.inflate(inflater, container, false)
        executor = ContextCompat.getMainExecutor(requireContext())
        binding.cameraButton.setOnClickListener {
            takePhoto()
        }
        binding.galleryButton.setOnClickListener {
            findNavController().navigate(R.id.action_cameraFragment_to_galleryFragment)
        }
        binding.mapButton.setOnClickListener {
            findNavController().navigate(R.id.action_cameraFragment_to_mapsFragment)
        }
        checkPermissions()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        App.NOTIFICATION_ID = 0
    }


    private fun checkPermissions() {
        val isAllGranted = REQUEST_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (isAllGranted) {
            startCamera()
        } else {
            launcher.launch(REQUEST_PERMISSIONS)
        }
        shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            imageCapture = ImageCapture.Builder().build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, executor)
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    createNotification(
                        "Image saved",
                        "Photo saved on: ${outputFileResults.savedUri}"
                    )
                    val currentDate = Date()
                    val formatter = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
                    val formattedDate = formatter.format(currentDate)
                    val photo =
                        Photo(uri = outputFileResults.savedUri.toString(), date = formattedDate)

                    val repository = PhotoRepository(requireContext().applicationContext)
                    lifecycleScope.launch {
                        repository.addPhoto(photo)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    createNotification(
                        "Image not saved",
                        "Photo saved on: failed"
                    )
                    exception.printStackTrace()
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun createNotification(title: String, description: String) {
        val intent = Intent(requireContext(), MainActivity::class.java)
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.getActivities(
                requireContext(),
                0,
                arrayOf(intent),
                PendingIntent.FLAG_IMMUTABLE
            )
        else
            PendingIntent.getActivities(
                requireContext(),
                0,
                arrayOf(intent),
                PendingIntent.FLAG_UPDATE_CURRENT
            )

        val notification = NotificationCompat.Builder(requireContext(), App.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(requireContext()).notify(App.NOTIFICATION_ID, notification)
        App.NOTIFICATION_ID++
    }

    companion object {
        private val REQUEST_PERMISSIONS: Array<String> = buildList {
            add(android.Manifest.permission.CAMERA)
            add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
}