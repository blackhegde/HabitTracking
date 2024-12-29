package com.example.layout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.sqrt

class DashboardActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var tvTimer: TextView
    private lateinit var tvMode: TextView
    private lateinit var btnStop: Button

    private var seconds = 0
    private var running = true
    private var totalDistance = 0.0 // Tổng quãng đường đã chạy (km)
    private var lastLocation: GeoPoint? = null // Lưu vị trí cuối cùng
    private val points: MutableList<GeoPoint> = mutableListOf() // Lưu danh sách các điểm

    // FusedLocationProviderClient
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var polyline: Polyline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cấu hình osmdroid
        Configuration.getInstance().load(applicationContext, applicationContext.getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.dashboard_activity)

        // Kết nối các View
        mapView = findViewById(R.id.mapView)
        tvTimer = findViewById(R.id.tvTimer)
        tvMode = findViewById(R.id.tvMode)
        btnStop = findViewById(R.id.btnStop)

        // Hiển thị chế độ đã chọn
        val mode = intent.getStringExtra("mode") ?: "Không rõ"
        tvMode.text = "Chế độ: $mode"

        // Cấu hình MapView
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Tạo Polyline cho đường đi
        polyline = Polyline()
        polyline.color = android.graphics.Color.BLUE // Màu của đường
        polyline.width = 5.0f // Độ dày của đường
        mapView.overlays.add(polyline)

        // Bắt đầu đếm thời gian
        runTimer()

        // Dừng chạy khi nhấn nút Stop
        btnStop.setOnClickListener {
            running = false
            showSummaryPopup()
        }

        // Khởi tạo FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Cấu hình yêu cầu vị trí
        locationRequest = LocationRequest.create().apply {
            interval = 5000 // 5 giây
            fastestInterval = 2000 // 2 giây
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Kiểm tra quyền và bắt đầu theo dõi vị trí
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            startTrackingLocation()
        }
    }

    private fun startTrackingLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val newLocation = locationResult.lastLocation
            if (newLocation != null) {
                val geoPoint = GeoPoint(newLocation.latitude, newLocation.longitude)
                if (lastLocation == null) {
                    // Nếu lastLocation chưa có giá trị, khởi tạo Marker tại vị trí đầu tiên
                    lastLocation = geoPoint
                    points.add(geoPoint)
                    addMarker(geoPoint, "Điểm bắt đầu")
                } else {
                    updatePath(geoPoint) // Cập nhật đường đi
                }
            }
        }
    }

    private fun runTimer() {
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60

                // Hiển thị thời gian trên TextView
                tvTimer.text = String.format("Thời gian: %02d:%02d:%02d", hours, minutes, secs)

                if (running) {
                    seconds++
                }
                handler.postDelayed(this, 1000) // Cập nhật mỗi giây
            }
        })
    }

    private fun updatePath(newPoint: GeoPoint) {
        // Thêm điểm mới vào danh sách
        points.add(newPoint)

        // Tính khoảng cách giữa điểm mới và điểm trước đó
        lastLocation?.let { last ->
            val distance = haversine(last.latitude, last.longitude, newPoint.latitude, newPoint.longitude)
            totalDistance += distance
        }

        // Cập nhật Polyline với danh sách điểm
        polyline.setPoints(points)
        if (points.size % 5 == 0) { // Chỉ làm mới sau mỗi 5 điểm
            mapView.invalidate()
        }
        mapView.invalidate() // Làm mới MapView
        lastLocation = newPoint // Cập nhật lastLocation
    }

    private fun addMarker(location: GeoPoint, title: String) {
        val marker = Marker(mapView)
        marker.position = location
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = title
        mapView.overlays.add(marker)
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Bán kính Trái Đất (km)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }

    private fun showSummaryPopup() {
        val avgSpeed = if (seconds > 0) totalDistance / (seconds / 3600.0) else 0.0 // Tốc độ trung bình (km/h)

        AlertDialog.Builder(this)
            .setTitle("Kết quả")
            .setMessage(
                String.format(
                    "Thời gian chạy: %02d:%02d:%02d\nQuãng đường: %.2f km\nTốc độ trung bình: %.2f km/h",
                    seconds / 3600, (seconds % 3600) / 60, seconds % 60,
                    totalDistance, avgSpeed
                )
            )
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                finish() // Quay lại màn hình chính
            }
            .create()
            .show()
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}