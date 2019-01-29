package e.demokracia.appmagyar

import kotlinx.coroutines.Deferred
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Interface of the AppPoster service.
 */
interface AppPoster {

    /**
     * Post multipart data to app_poster.pl service.
     *
     * @param email the email address
     * @param dc the temporary email address
     * @param idcard the ID card image
     * @param adcard_1 the AD card one image
     * @param adcard_2 the AD card two image
     * @param portrait the portrait image
     * @return the response
     */
    @Multipart
    @POST(value = "app_poster.pl")
    fun post(
        @Part("email") email: RequestBody,
        @Part("dc") dc: RequestBody?,
        @Part idcard: MultipartBody.Part,
        @Part adcard_1: MultipartBody.Part,
        @Part adcard_2: MultipartBody.Part,
        @Part portrait: MultipartBody.Part
    ): Deferred<Response<AppPosterResponse>>
}
