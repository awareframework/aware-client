AWARE: OpenWeather
==========================

[![Release](https://jitpack.io/v/denzilferreira/com.aware.plugin.openweather.svg)](https://jitpack.io/#denzilferreira/com.aware.plugin.openweather)

This plugin uses Google Fused Location in low power mode and OpenWeather API to provide the weather conditions where the user is.

# Settings
Parameters adjusted on the dashboard and client:
* **status_plugin_openweather**: (boolean) activate/deactivate plugin
* **plugin_openweather_frequency**: (integer) How frequently to fetch weather information (in minutes), default 60 minutes
* **units_plugin_openweather**: (string) imperial or metric, default metric
* **api_key_plugin_openweather**: (string) OpenWeather API key. Get your free API key from openweathermap.org

# Broadcasts
**ACTION_AWARE_PLUGIN_OPENWEATHER**
Broadcasted when we have a new weather conditions report, with the following extras:
- **openweather**: (ContentValues) latest weather information
    
# Providers
##  Locations Data
> content://com.aware.plugin.openweather.provider.openweather/plugin_openweather

Field | Type | Description
----- | ---- | -----------
_id | INTEGER | primary key auto-incremented
timestamp | REAL | unix timestamp in milliseconds of sample
device_id | TEXT | AWARE device ID
city | TEXT | weather's city
temperature	| REAL | current atmospheric temperature
temperature_max | REAL | forecast highest temperature
temperature_min | REAL | forecast lowest temperature
unit | TEXT | measurement unit (metric, imperial)
humidity | REAL | forecast humidity percentage
pressure | REAL | atmospheric pressure
wind_speed | REAL | wind's speed in m/s
wind_degrees | REAL | wind's direction
cloudiness | REAL | percent amount of clouds in the sky
rain | REAL | amount of rain in past hour, in millimeters
snow | REAL | amount of snow in past hour, in millimeters
sunrise | REAL | timestamp of sunrise
sunset | REAL | timestamp of sunset
weather_icon_id | INTEGER | icon ID from OpenWeather
weather_description | TEXT | forecast description