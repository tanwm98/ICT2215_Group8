package com.example.helloworld

import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private var spawnCount = 0
    private val allImages = mutableListOf<ImageView>()
    private val activeMediaPlayers = mutableListOf<MediaPlayer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set the background image from the assets folder
        setBackgroundFromAssets("wp.jpg")

        val overlay = findViewById<FrameLayout>(R.id.overlay)

        // Set up the spawn button
        findViewById<Button>(R.id.spawnButton).setOnClickListener {
            handleSpawnButtonClick(overlay)
        }
    }

    /**
     * Dynamically sets the background image from the assets directory
     */
    private fun setBackgroundFromAssets(assetFileName: String) {
        try {
            val inputStream = assets.open(assetFileName)
            val drawable = Drawable.createFromStream(inputStream, null)
            inputStream.close()

            window.decorView.background = drawable
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Handles the Spawn Button click to add GIFs dynamically
     */
    private fun handleSpawnButtonClick(overlay: FrameLayout) {
        spawnCount++

        // Load a random GIF from the assets/xmm folder
        val randomGif = getRandomFileFromAssets("xmm")
        randomGif?.let { createBouncingGif(it) }

        // Play audio from assets (allow overlapping)
        playAudioFromAssets("xmm.mp3")

        if (spawnCount == 50) {
            createSeptemberButton(overlay)
        }
    }

    /**
     * Creates and animates a bouncing GIF from assets
     */
    private fun createBouncingGif(assetPath: String) {
        val gifView = ImageView(this)
        try {
            val inputStream = assets.open(assetPath)
            val drawable = Drawable.createFromStream(inputStream, null)
            inputStream.close()

            gifView.setImageDrawable(drawable)
            gifView.layoutParams = FrameLayout.LayoutParams(200, 200)
            (findViewById<FrameLayout>(android.R.id.content)).addView(gifView)
            allImages.add(gifView)

            // Animate the GIF
            var dx = Random.nextFloat() * 4 + 1
            var dy = Random.nextFloat() * 4 + 1
            var x = Random.nextInt(0, resources.displayMetrics.widthPixels - 200)
            var y = Random.nextInt(0, resources.displayMetrics.heightPixels - 200)

            val runnable = object : Runnable {
                override fun run() {
                    x += dx.toInt()
                    y += dy.toInt()

                    if (x <= 0 || x >= resources.displayMetrics.widthPixels - 200) dx = -dx
                    if (y <= 0 || y >= resources.displayMetrics.heightPixels - 200) dy = -dy

                    gifView.x = x.toFloat()
                    gifView.y = y.toFloat()

                    gifView.postDelayed(this, 16)
                }
            }
            gifView.post(runnable)

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Creates a special button and effect when the spawn count reaches 50
     */
    private fun createSeptemberButton(overlay: FrameLayout) {
        val button = Button(this).apply {
            text = "September"
            setOnClickListener { triggerSpecialEffect(overlay) }
        }
        addContentView(button, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = 16
            topMargin = 16
        })
    }

    /**
     * Triggers the special September effect
     */
    private fun triggerSpecialEffect(overlay: FrameLayout) {
        overlay.alpha = 1f
        allImages.forEach { it.setImageDrawable(loadDrawableFromAssets("o.png")) }
        playAudioFromAssets("911.mp3") // Play the special effect audio
    }

    /**
     * Plays audio from the assets directory, allowing overlap
     */
    private fun playAudioFromAssets(audioFile: String) {
        try {
            val afd = assets.openFd(audioFile)
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
                start()
            }
            activeMediaPlayers.add(mediaPlayer)
            afd.close()

            // Remove the MediaPlayer instance from the list when playback completes
            mediaPlayer.setOnCompletionListener {
                activeMediaPlayers.remove(mediaPlayer)
                mediaPlayer.release()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Loads a drawable from the assets directory
     */
    private fun loadDrawableFromAssets(assetPath: String): Drawable? {
        return try {
            val inputStream = assets.open(assetPath)
            Drawable.createFromStream(inputStream, null).also { inputStream.close() }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Gets a random file from a folder in the assets directory
     */
    private fun getRandomFileFromAssets(folder: String): String? {
        return try {
            val files = assets.list(folder)
            files?.random()?.let { "$folder/$it" }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release all active MediaPlayer instances
        activeMediaPlayers.forEach { it.release() }
        activeMediaPlayers.clear()
    }
}
