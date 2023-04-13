package com.example.weatherapp.utilities

import com.example.weatherapp.pojo.Model
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiInterface {

    @GET("weather")
    fun getCurrentWeatherData(
        @Query("lat") latitude: String,
        @Query("lon") longitude: String,
        @Query("appid") api_key: String
    ): Call<Model>

    @GET("weather")
    fun getCityWeatherData(
        @Query("q") city_name: String,
        @Query("appid") api_key: String
    ): Call<Model>
}