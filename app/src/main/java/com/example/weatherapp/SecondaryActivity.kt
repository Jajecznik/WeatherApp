package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.example.weatherapp.databinding.ActivitySecondaryBinding
import com.example.weatherapp.pojo.Model
import com.example.weatherapp.utilities.ApiUtilities
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.*

class SecondaryActivity : AppCompatActivity() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var activitySecondaryBinding: ActivitySecondaryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activitySecondaryBinding = DataBindingUtil.setContentView(this, R.layout.activity_secondary)
        supportActionBar?.hide()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        activitySecondaryBinding.rlMainLayout.visibility = View.GONE

        getCurrentLocation()

        // przejście do głównego widoku
        activitySecondaryBinding.btnNewView.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // pobranie miejscowości wpisanej przez użytkownika
        activitySecondaryBinding.etGetCityName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val cityName = activitySecondaryBinding.etGetCityName.text.toString().trim()
                if (cityName.isNotEmpty()) {
                    getCityWeather(cityName)
                    val view = this.currentFocus
                    if (view != null) {
                        val imm: InputMethodManager =
                            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(view.windowToken, 0)
                        activitySecondaryBinding.etGetCityName.clearFocus()
                    }
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    // pobranie danych pogodowych dla wybranego miasta
    private fun getCityWeather(cityName: String) {
        activitySecondaryBinding.pbLoading.visibility = View.VISIBLE
        ApiUtilities.getApiInterface()?.getCityWeatherData(cityName, API_KEY)?.enqueue(object: Callback<Model> {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResponse(call: Call<Model>, response: Response<Model>) {
                if (response.isSuccessful && response.body() != null) {
                    setDataOnViews(response.body())
                } else {
                    Toast.makeText(applicationContext, "Not a valid city name", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Model>, t: Throwable) {
                Toast.makeText(applicationContext, "Not a valid city name", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // pobranie aktualnej lokalizacji użytkownika
    private fun getCurrentLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermission()
                    return
                }
                fusedLocationProviderClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        fetchCurrentLocationWeather(location.latitude.toString(), location.longitude.toString())
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(applicationContext, "Unable to get location: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Turn on location", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {
            requestPermission()
        }
    }

    // pobranie informacji o pogodzie dla aktualnej lokalizacji
    private fun fetchCurrentLocationWeather(latitude: String, longitude: String) {
        activitySecondaryBinding.pbLoading.visibility = View.VISIBLE
        ApiUtilities.getApiInterface()?.getCurrentWeatherData(latitude, longitude, API_KEY)?.enqueue(object: Callback<Model> {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResponse(call: Call<Model>, response: Response<Model>) {
                if (response.isSuccessful) {
                    setDataOnViews(response.body())
                }
            }
            override fun onFailure(call: Call<Model>, t: Throwable) {
                Toast.makeText(applicationContext, "ERROR", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // wczytanie danych na ekran
    @SuppressLint("SetTextI18n", "SimpleDateFormat")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setDataOnViews(body: Model?) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm")
        val currentDate = sdf.format(Date())

        activitySecondaryBinding.tvDateAndTime.text = currentDate
        activitySecondaryBinding.tvCityName.text = body!!.name
        activitySecondaryBinding.tvTemp.text = kelvinToCelsius(body.main.temp).toString() + "°C"
        activitySecondaryBinding.tvDescription.text = body.weather[0].description
        activitySecondaryBinding.tvSunrise.text = timeStampToLocalDate(body.sys.sunrise.toLong()).dropLast(3)
        activitySecondaryBinding.tvSunset.text = timeStampToLocalDate(body.sys.sunset.toLong()).dropLast(3)
        activitySecondaryBinding.tvPressure.text = body.main.pressure.toString() + " hPa"
        activitySecondaryBinding.tvClouds.text = body.clouds.all.toString() + "%"
        activitySecondaryBinding.tvHumidity.text = body.main.humidity.toString() + "%"
        activitySecondaryBinding.tvWindSpeed.text = body.wind.speed.toString() + " m/s"
        activitySecondaryBinding.etGetCityName.setText(body.name)

        updateUI(body.weather[0].id)
    }

    // konwersja czasu na lokalną datę i godzinę
    @RequiresApi(Build.VERSION_CODES.O)
    private fun timeStampToLocalDate(timeStamp: Long): String {
        val localTime = timeStamp.let {
            Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalTime()
        }
        return localTime.toString()
    }

    // ustawienie odpowiedniej ikony w zależności od rodzaju pogody
    private fun updateUI(id: Int) {
        if (id in 200..804) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            when (id) {
                in 200..232 -> {
                    activitySecondaryBinding.ivWeatherIcon.setImageResource(R.drawable.d11)
                }
                in 300..321, in 520..531 -> {
                    activitySecondaryBinding.ivWeatherIcon.setImageResource(R.drawable.d09)
                }
                in 500..504 -> {
                    activitySecondaryBinding.ivWeatherIcon.setImageResource(R.drawable.d10)
                }
                511, in 600..622 -> {
                    activitySecondaryBinding.ivWeatherIcon.setImageResource(R.drawable.d13)
                }
                in 701..781 -> {
                    activitySecondaryBinding.ivWeatherIcon.setImageResource(R.drawable.d50)
                }
                800 -> {
                    activitySecondaryBinding.ivWeatherIcon.setImageResource(R.drawable.d01)
                }
                801 -> {
                    activitySecondaryBinding.ivWeatherIcon.setImageResource(R.drawable.d02)
                }
                802 -> {
                    activitySecondaryBinding.ivWeatherIcon.setImageResource(R.drawable.d03)
                }
                in 803..804 -> {
                    activitySecondaryBinding.ivWeatherIcon.setImageResource(R.drawable.d04)
                }
            }
        }
        activitySecondaryBinding.pbLoading.visibility = View.GONE
        activitySecondaryBinding.rlMainLayout.visibility = View.VISIBLE
    }

    // konwersja temperatury
    private fun kelvinToCelsius(temp: Double): Double {
        var intTemp = temp
        intTemp = intTemp.minus(273)
        return intTemp.toBigDecimal().setScale(1, RoundingMode.UP).toDouble()
    }

    // sprawdzenie czy lokalizacja jest włączona
    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // okienko z zezwoleniem na dostep do lakolizacji
    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_ACCESS_LOCATION)
    }

    // stałe
    companion object {
        private const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
        const val API_KEY = "40f400cd97960044f14fe4f14901697b"
    }

    // sprawdzenie czy aplikacja ma pozwolenia
    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        return false
    }

    // sprawdzenie czy przyznane zostały uprawnienia
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == PERMISSION_REQUEST_ACCESS_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(applicationContext, "Granted", Toast.LENGTH_SHORT).show()
                getCurrentLocation()
            } else {
                Toast.makeText(applicationContext, "Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}