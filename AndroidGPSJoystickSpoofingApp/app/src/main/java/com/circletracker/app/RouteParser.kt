package com.circletracker.app

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader

class RouteParser {
    fun parseGpx(context: Context, inputStream: InputStream): List<LatLng> {
        val points = mutableListOf<LatLng>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var lat: Double? = null
            var lon: Double? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "trkpt", "wpt" -> {
                                lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                if (lat != null && lon != null) {
                                    points.add(LatLng(lat, lon))
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return points
    }

    fun parseKml(context: Context, inputStream: InputStream): List<LatLng> {
        val points = mutableListOf<LatLng>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var coordinates = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "coordinates") {
                            eventType = parser.next()
                            if (eventType == XmlPullParser.TEXT) {
                                coordinates = parser.text.trim()
                                parseKmlCoordinates(coordinates, points)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return points
    }

    private fun parseKmlCoordinates(coordinates: String, points: MutableList<LatLng>) {
        coordinates.split("\\s+".toRegex()).forEach { coordinate ->
            val parts = coordinate.split(",")
            if (parts.size >= 2) {
                try {
                    val lon = parts[0].toDouble()
                    val lat = parts[1].toDouble()
                    points.add(LatLng(lat, lon))
                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun interpolateRoute(points: List<LatLng>, intervalMeters: Double): List<LatLng> {
        if (points.size < 2) return points

        val interpolatedPoints = mutableListOf<LatLng>()
        interpolatedPoints.add(points[0])

        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]
            
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                start.latitude, start.longitude,
                end.latitude, end.longitude,
                results
            )
            val distance = results[0]

            if (distance > intervalMeters) {
                val segments = (distance / intervalMeters).toInt()
                for (j in 1 until segments) {
                    val fraction = j.toDouble() / segments
                    val lat = start.latitude + (end.latitude - start.latitude) * fraction
                    val lng = start.longitude + (end.longitude - start.longitude) * fraction
                    interpolatedPoints.add(LatLng(lat, lng))
                }
            }
            interpolatedPoints.add(end)
        }

        return interpolatedPoints
    }
}
