package e.demokracia.appmagyar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.android.synthetic.main.activity_main.*
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.FileProvider
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Main activity of "ADA webmagyar tanúsítvány" client application.
 */
class MainActivity : AppCompatActivity() {

    private var tempImageFile: File? = null
    private var idcardPhotoFlag: Boolean = true
    private var adcard1PhotoFlag: Boolean = true
    private var adcard2PhotoFlag: Boolean = true
    private var portraitPhotoFlag: Boolean = true

    /**
     * OnCreate hook.
     *
     * @param savedInstanceState the saved instance state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        temporary_email.setOnClickListener {
            email.isEnabled = !temporary_email.isChecked
            email.setText("")
        }

        photo_idcard_button.setOnClickListener { dispatchTakePictureIntent(1) }
        photo_adcard_1_button.setOnClickListener { dispatchTakePictureIntent(2) }
        photo_adcard_2_button.setOnClickListener { dispatchTakePictureIntent(3) }
        photo_portrait_button.setOnClickListener { dispatchTakePictureIntent(4) }

        send_button.setOnClickListener { sendToServer() }
    }

    /**
     * Reset the form.
     */
    private fun resetForm() {
        idcardPhotoFlag = true
        adcard1PhotoFlag = true
        adcard2PhotoFlag = true
        portraitPhotoFlag = true

        email.setText("")
        temporary_email.isChecked = false
        photo_idcard.setImageResource(R.drawable.placeholder)
        photo_adcard_1.setImageResource(R.drawable.placeholder)
        photo_adcard_2.setImageResource(R.drawable.placeholder)
        photo_portrait.setImageResource(R.drawable.placeholder)
    }

    /**
     * Dispatch to the take picture intent (with request code).
     *
     * @param requestCode the request code
     */
    private fun dispatchTakePictureIntent(requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
            return
        }

        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                tempImageFile = getOutputMediaFile()
                if (tempImageFile == null) return

                val tempImageFileUri = FileProvider.getUriForFile(MainActivity@ this, BuildConfig.APPLICATION_ID + ".provider", tempImageFile!!)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempImageFileUri)
                startActivityForResult(takePictureIntent, requestCode)
            }
        }
    }

    /**
     * OnActivityResult hook.
     *
     * @param requestCode the request code
     * @param resultCode the result code
     * @param data the option data
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        /**
         * Permission request result.
         */
        if (requestCode == 100) {
            return
        }

        /**
         * Take a photo result.
         */
        if (resultCode == RESULT_OK) {
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            val imageBitmap = BitmapFactory.decodeFile(tempImageFile!!.path, options)
            tempImageFile!!.delete()

            val rotatedBitmap = if (imageBitmap.width < imageBitmap.height) {
                val matrix = Matrix()
                matrix.setRotate(90.0f, imageBitmap.width.toFloat() / 2, imageBitmap.height.toFloat() / 2)
                Bitmap.createBitmap(imageBitmap, 0, 0, imageBitmap.width, imageBitmap.height, matrix, true)
            } else {
                imageBitmap
            }
            Log.i(this.javaClass.simpleName, "Width: ${rotatedBitmap.width}, Height: ${rotatedBitmap.height}")

            val scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap, 1024, 1024 * rotatedBitmap.height / rotatedBitmap.width, false)
            Log.i(this.javaClass.simpleName, "Width: ${scaledBitmap.width}, Height: ${scaledBitmap.height}")

            when (requestCode) {
                1 -> {
                    photo_idcard.setImageBitmap(scaledBitmap)
                    idcardPhotoFlag = false
                }
                2 -> {
                    photo_adcard_1.setImageBitmap(scaledBitmap)
                    adcard1PhotoFlag = false
                }
                3 -> {
                    photo_adcard_2.setImageBitmap(scaledBitmap)
                    adcard2PhotoFlag = false
                }
                4 -> {
                    photo_portrait.setImageBitmap(scaledBitmap)
                    portraitPhotoFlag = false
                }
            }
        }
    }

    /**
     * Send data to the server.
     */
    private fun sendToServer() {
        if (idcardPhotoFlag || adcard1PhotoFlag || adcard2PhotoFlag || portraitPhotoFlag) {
            Toast.makeText(this, "Mind a négy fotó szükséges az azonosításhoz!", Toast.LENGTH_LONG).show()
        }

        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(
            Date::class.java,
            JsonDeserializer<Date> { j, _, _ -> Date(j.asLong) })
        gsonBuilder.registerTypeAdapter(
            Date::class.java,
            JsonSerializer<Date> { s, _, _ -> JsonPrimitive(s.time) })
        val gson = gsonBuilder.create()

        val retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .baseUrl("https://webmagyar.demokracia.rulez.org/")
            .client(okHttpClient)
            .build()

        val idcardBitmap = (photo_idcard.drawable as BitmapDrawable).bitmap
        val idcardStream = ByteArrayOutputStream()
        idcardBitmap.compress(Bitmap.CompressFormat.JPEG, 50, idcardStream)
        val idcardByteArray = idcardStream.toByteArray()

        val adcard1Bitmap = (photo_adcard_1.drawable as BitmapDrawable).bitmap
        val adcard1Stream = ByteArrayOutputStream()
        adcard1Bitmap.compress(Bitmap.CompressFormat.JPEG, 50, adcard1Stream)
        val adcard1ByteArray = adcard1Stream.toByteArray()

        val adcard2Bitmap = (photo_adcard_2.drawable as BitmapDrawable).bitmap
        val adcard2Stream = ByteArrayOutputStream()
        adcard2Bitmap.compress(Bitmap.CompressFormat.JPEG, 50, adcard2Stream)
        val adcard2ByteArray = adcard2Stream.toByteArray()

        val portraitBitmap = (photo_portrait.drawable as BitmapDrawable).bitmap
        val portraitStream = ByteArrayOutputStream()
        portraitBitmap.compress(Bitmap.CompressFormat.JPEG, 50, portraitStream)
        val portraitByteArray = portraitStream.toByteArray()

        val appPoster = retrofit.create(AppPoster::class.java)

        val emailBody = RequestBody.create(MediaType.get("text/plain"), email.text.toString())
        val dcBody = RequestBody.create(MediaType.get("text/plain"), if (temporary_email.isChecked) "1" else "0")
        val idcardBody = RequestBody.create(MediaType.get("image/jpeg"), idcardByteArray)
        val adcard1Body = RequestBody.create(MediaType.get("image/jpeg"), adcard1ByteArray)
        val adcard2Body = RequestBody.create(MediaType.get("image/jpeg"), adcard2ByteArray)
        val portraitBody = RequestBody.create(MediaType.get("image/jpeg"), portraitByteArray)

        val idcardBodyPart = MultipartBody.Part.createFormData("idcard", "idcard.jpg", idcardBody)
        val adcard1BodyPart = MultipartBody.Part.createFormData("adcard_1", "adcard_1.jpg", adcard1Body)
        val adcard2BodyPart = MultipartBody.Part.createFormData("adcard_2", "adcard_2.jpg", adcard2Body)
        val portraitBodyPart = MultipartBody.Part.createFormData("portrait", "portrait.jpg", portraitBody)

        coroutinesCallHelper(
            { appPoster.post(emailBody, dcBody, idcardBodyPart, adcard1BodyPart, adcard2BodyPart, portraitBodyPart) },
            { response -> checkResult(response) },
            { t -> checkError(t) }
        )
    }

    /**
     * Check the response of the server.
     *
     * @param response the response
     */
    private fun checkResult(response: Response<AppPosterResponse>) {
        if (response.code() != 200) {
            Toast.makeText(this, "Hibás válasz érkezett a szervertől...", Toast.LENGTH_LONG).show()
            return
        }

        if (response.body() == null) {
            Toast.makeText(this, "Nem érkezett válasz a szervertől...", Toast.LENGTH_LONG).show()
            return
        }

        val responseBody = response.body()!!
        responseBody.errors.forEach { error ->
            error.positioned_errors.forEach { pe ->
                Toast.makeText(this, "${error.related_to}: ${pe.message} (${pe.code})", Toast.LENGTH_LONG).show()
            }
        }

        if (responseBody.errors.isEmpty()) {
            resetForm()
        }
    }

    /**
     * Other unknown error...
     *
     * @param t the throwable
     */
    private fun checkError(t: Throwable) {
        Toast.makeText(this, "Ismeretlen hiba (${t.message})", Toast.LENGTH_LONG).show()
    }

    /**
     * Show progress.
     */
    private fun showLoading() {
        ada_progress.visibility = View.VISIBLE
    }

    /**
     * Hide progress.
     */
    private fun hideLoading() {
        ada_progress.visibility = View.INVISIBLE
    }

    /**
     * Coroutines based call helper.
     *
     * @param call the backend call
     * @param onSuccess the success path function
     * @param onError the custom onError function
     */
    private fun <T> coroutinesCallHelper(
        call: () -> Deferred<T>,
        onSuccess: (response: T) -> Unit,
        onError: (t: Throwable) -> Unit
    ) = GlobalScope.launch(Dispatchers.Main) {
        try {
            showLoading()
            onSuccess(call().await())
        } catch (t: Throwable) {
            onError(t)
        } finally {
            hideLoading()
        }
    }

    /**
     * Create a temporary file in the app's storage directory.
     *
     * @return the file
     */
    private fun getOutputMediaFile(): File? {
        val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AppMagyar")

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null
            }
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss").format(Date())
        return File(mediaStorageDir.path + File.separator + "IMG_" + timestamp + ".jpg")
    }
}
