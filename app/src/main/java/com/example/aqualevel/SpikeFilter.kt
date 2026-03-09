package com.example.aqualevel

object SpikeFilter {
    fun smoothDistances(distances: DoubleArray): DoubleArray {
        if (distances.size < 3) return distances

        val smoothed = DoubleArray(distances.size)
        // Keep the first reading as is
        smoothed[0] = distances[0]
        
        for (i in 1 until distances.size - 1) {
            val prev = distances[i - 1]
            val curr = distances[i]
            val next = distances[i + 1]
            if ((curr - prev > 20 && curr - next > 20) || (prev - curr > 20 && next - curr > 20)) {
                smoothed[i] = (prev + next) / 2
            } else {
                smoothed[i] = curr
            }
        }
        
        // Handle the last reading (current live reading)
        val lastIdx = distances.size - 1
        val diffFromPrev = distances[lastIdx] - smoothed[lastIdx - 1]
        
        // If the last reading is a huge spike, we cap it until confirmed by the next reading
        if (Math.abs(diffFromPrev) > 20) {
            smoothed[lastIdx] = smoothed[lastIdx - 1]
        } else {
            smoothed[lastIdx] = distances[lastIdx]
        }
        
        return smoothed
    }
}
