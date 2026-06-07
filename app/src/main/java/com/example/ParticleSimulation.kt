package com.example

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val radius: Float = 8f
)

class ParticleSimulator {
    val particles = mutableStateListOf<Particle>()
    
    // Manage particle pool count efficiently
    fun setParticleCount(targetCount: Int, containerWidth: Float, containerHeight: Float) {
        val currentSize = particles.size
        if (targetCount == currentSize) return
        
        if (targetCount < currentSize) {
            // Remove excess particles safely
            while (particles.size > targetCount) {
                particles.removeAt(particles.size - 1)
            }
        } else {
            // Add new randomized particles
            val toAdd = targetCount - currentSize
            val colors = listOf(
                Color(0xFF22C55E), // Neon Emerald Green
                Color(0xFF3B82F6), // Electric Azure Blue
                Color(0xFFF59E0B), // Vibrant Warning Amber
                Color(0xFFEF4444), // Crimson Alert Red
                Color(0xFFA855F7), // Acid Violet Purple
                Color(0xFF06B6D4)  // Neon Cyan
            )
            for (i in 0 until toAdd) {
                val randX = Random.nextFloat() * (if (containerWidth > 0) containerWidth else 800f)
                val randY = Random.nextFloat() * (if (containerHeight > 0) containerHeight else 800f)
                val randAngle = Random.nextFloat() * 2 * Math.PI
                val speed = 2f + Random.nextFloat() * 6f
                val vx = (cos(randAngle) * speed).toFloat()
                val vy = (sin(randAngle) * speed).toFloat()
                val color = colors[Random.nextInt(colors.size)]
                val size = 6f + Random.nextFloat() * 12f
                
                particles.add(Particle(randX, randY, vx, vy, color, size))
            }
        }
    }

    // Step physics & run simulated heavy stress
    fun update(
        width: Float,
        height: Float,
        physicsOn: Boolean,
        speedMultiplier: Float,
        resolutionPreset: ScreenResolutionPreset,
        cpuStressMultiplier: Int, // 0 = None, 1 - 20 = incremental math load
        nodeConnectionsOn: Boolean // O(N^2) lines drawing matrix option
    ) {
        val count = particles.size
        if (count == 0 || width <= 0f || height <= 0f) return

        // Artificially simulate heavy screen-fill calculations or high-density grids
        // Multiply work based on preset weight
        if (resolutionPreset.basePagingWeight > 1) {
            // Perform dummy drawing raster calculations to heat up frame intervals
            var sum = 0.0
            val loadLimit = resolutionPreset.basePagingWeight * 2000
            for (i in 0..loadLimit) {
                sum += sin(i.toDouble()) * cos(i.toDouble())
            }
        }

        // Apply CPU math cruncher to simulate lower hardware limits on active thread
        if (cpuStressMultiplier > 0) {
            val iterations = cpuStressMultiplier * 150_000
            var sum = 0.1
            for (i in 0 until iterations) {
                sum = sin(sum) * cos(sum) + 0.05
            }
        }

        // 1. Move and bounce on walls
        for (i in 0 until count) {
            val p = particles[i]
            p.x += p.vx * speedMultiplier
            p.y += p.vy * speedMultiplier

            // Handle boundaries with margin so they don't get stuck
            val minX = p.radius
            val maxX = width - p.radius
            val minY = p.radius
            val maxY = height - p.radius

            if (p.x < minX) {
                p.x = minX
                p.vx = -p.vx
            } else if (p.x > maxX) {
                p.x = maxX
                p.vx = -p.vx
            }

            if (p.y < minY) {
                p.y = minY
                p.vy = -p.vy
            } else if (p.y > maxY) {
                p.y = maxY
                p.vy = -p.vy
            }
        }

        // 2. Perform N^2 physics sphere rigid body colliders (Intensive calculations)
        if (physicsOn && count > 1) {
            // Cap O(N^2) checks to 1500 particles to avoid complete freezing, but enough to trigger true dropped frames!
            val mathLimit = count.coerceAtMost(1000)
            for (i in 0 until mathLimit) {
                val p1 = particles[i]
                for (j in (i + 1) until mathLimit) {
                    val p2 = particles[j]
                    val dx = p2.x - p1.x
                    val dy = p2.y - p1.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val minDist = p1.radius + p2.radius
                    if (dist < minDist && dist > 0.01f) {
                        // Elastic bounce heading swaps
                        val normalX = dx / dist
                        val normalY = dy / dist
                        
                        // Relative velocity along collision standard
                        val kx = p1.vx - p2.vx
                        val ky = p1.vy - p2.vy
                        val p = 2f * (normalX * kx + normalY * ky) / 2f
                        
                        p1.vx -= p * normalX
                        p1.vy -= p * normalY
                        p2.vx += p * normalX
                        p2.vy += p * normalY

                        // Displace overlap to prevent locking
                        val overlap = minDist - dist
                        p1.x -= normalX * overlap * 0.51f
                        p1.y -= normalY * overlap * 0.51f
                        p2.x += normalX * overlap * 0.51f
                        p2.y += normalY * overlap * 0.51f
                    }
                }
            }
        }
    }
}
