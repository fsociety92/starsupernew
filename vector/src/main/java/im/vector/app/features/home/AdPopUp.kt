package com.contusfly.views

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import androidx.core.graphics.drawable.DrawableCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import im.vector.app.R
import im.vector.app.core.extensions.singletonEntryPoint
import im.vector.app.core.glide.GlideApp
import im.vector.app.features.navigation.Navigator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class AdPopUp(
        activity: Activity,
        context: Context,
        title: String,
        description: String,
        bannerSrc: String,
        ad: JSONObject,
        rootUrl: String
) : Dialog(context, R.style.Theme_Dialog) {
    var activity: Activity?
    var title: String
    var description: String
    var bannerSrc: String
    var ad: JSONObject
    private var rootUrl: String
    protected var navigator: Navigator

    init {
        this.activity = activity
        this.title = title
        this.description = description
        this.bannerSrc = bannerSrc
        this.ad = ad
        this.rootUrl = rootUrl

        val singletonEntryPoint = context.singletonEntryPoint()
        navigator = singletonEntryPoint.navigator()

        this.setCancelable(true);
        this.setCanceledOnTouchOutside(true);
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setGravity(Gravity.BOTTOM)
    }

    private fun applyMask(originalDrawable: Drawable, maskDrawable: Drawable, resources: Resources): Drawable {
        val maskedDrawable: Drawable = originalDrawable.mutate()
        DrawableCompat.setTintList(maskedDrawable, null) // Remove color filter to preserve original colors of the image

        val maskBitmap: Bitmap = getBitmapFromDrawable(maskDrawable)
        val originalBitmap: Bitmap = getBitmapFromDrawable(originalDrawable)

        val resultBitmap: Bitmap = Bitmap.createBitmap(maskBitmap.width, maskBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

        // Calculate the scaling factors for the original image to fill the mask space
        val scaleX: Float = maskBitmap.width.toFloat() / originalBitmap.width
        val scaleY: Float = maskBitmap.height.toFloat() / originalBitmap.height
        val scale = maxOf(scaleX, scaleY)

        // Calculate the centered position of the scaled image within the mask space
        val offsetX: Float = (maskBitmap.width - originalBitmap.width * scale) / 2
        val offsetY: Float = (maskBitmap.height - originalBitmap.height * scale) / 2

        // Draw the scaled and centered original image on the canvas
        canvas.drawBitmap(originalBitmap, null, RectF(offsetX, offsetY, offsetX + originalBitmap.width * scale, offsetY + originalBitmap.height * scale), null)
        canvas.drawBitmap(maskBitmap, 0f, 0f, paint)

        return BitmapDrawable(resources, resultBitmap)
    }

    private fun getBitmapFromDrawable(drawable: Drawable): Bitmap {
        val bitmap: Bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }


    private fun loadImageWithGlide(context: Context, imgUrl: String?, imgView: ImageView, errorImg: Drawable?, maskDrawable: Drawable?, resources: Resources) {
        if (imgUrl != null && imgUrl.isNotEmpty()) {
            val options = RequestOptions().placeholder(imgView.drawable ?: errorImg)
                    .error(errorImg).diskCacheStrategy(DiskCacheStrategy.ALL).priority(Priority.HIGH)
            val requestBuilder: RequestBuilder<Drawable> = GlideApp.with(context).asDrawable().sizeMultiplier(0.1f)

            Glide.with(context).load(imgUrl).thumbnail(requestBuilder).apply(options).into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: com.bumptech.glide.request.transition.Transition<in Drawable>?) {
                    val maskedDrawable = maskDrawable?.let { applyMask(resource, it, resources) } ?: resource
                    imgView.setImageDrawable(maskedDrawable)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    imgView.setImageDrawable(placeholder)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    imgView.setImageDrawable(errorDrawable)
                }
            })
        } else {
            imgView.setImageDrawable(errorImg)
        }
    }


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.fragment_ad_popup)

        window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        val textDescription = findViewById<TextView>(R.id.tv_ad_description)

        textDescription.movementMethod = ScrollingMovementMethod()
        textDescription.text = description

        (findViewById<TextView>(R.id.tv_ad_title)).text = title

        val maskDrawable: Drawable? = context.getDrawable(R.drawable.ad_mask)
        loadImageWithGlide(context, bannerSrc, findViewById(R.id.iv_ad_banner), null, maskDrawable, context.resources)

        val ivAdYoutube = findViewById<ImageView>(R.id.iv_ad_youtube)
        val ivAdInstagram = findViewById<ImageView>(R.id.iv_ad_instagram)
        val ivAdBigstar = findViewById<ImageView>(R.id.iv_ad_bigstar)
        val ivAdWebsite = findViewById<ImageView>(R.id.iv_ad_website)
        val ivAdTel = findViewById<ImageView>(R.id.iv_ad_phoneNumber)
        val ivAdWhatsApp = findViewById<ImageView>(R.id.iv_ad_whatsApp)

        val youtubeUrl = ad.getString("youtubeUrl")
        val instagramUrl = ad.getString("instagramUrl")
        val bigstarUrl = ad.getString("bigstarUrl")
        val websiteUrl = ad.getString("websiteUrl")

        val phoneNumber = ad.getString("phoneNumber")
        val telUrl = "tel:${phoneNumber}"
        val whatsAppUrl = "https://api.whatsapp.com/send?phone=${phoneNumber}&text=%D0%97%D0%B4%D1%80%D0%B0%D0%B2%D1%81%D1%82%D0%B2%D1%83%D0%B9%D1%82%D0%B5!%20%D0%9F%D0%B8%D1%88%D1%83%20%D0%B2%D0%B0%D0%BC%20%D0%B8%D0%B7%20Big%20Star%20messenger%20https://bigstar.netlify.app/"

        if (bigstarUrl.isNotEmpty()) {
            ivAdBigstar.setImageResource(R.drawable.ic_bigstar_active)
            ivAdBigstar.setOnClickListener {
                val okHttpClient = OkHttpClient()
                val request = Request.Builder()
                        .patch(FormBody.Builder().build())
                        .url(rootUrl + "/ads/" + ad.getString("uuid") + "/bigstar/click")
                        .build()
                okHttpClient.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        println(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                    }
                })

                navigator.openRoomMemberProfile(userId = bigstarUrl, roomId = null, context = context)
            }
        }


        if (instagramUrl.isNullOrEmpty()) {
            ivAdInstagram.setImageResource(R.drawable.ic_instagram)
            ivAdInstagram.isClickable = false
        } else {
            ivAdInstagram.setImageResource(R.drawable.ic_instagram)
            ivAdInstagram.setOnClickListener {
                val okHttpClient = OkHttpClient()
                val request = Request.Builder()
                        .patch(FormBody.Builder().build())
                        .url(rootUrl + "/ads/" + ad.getString("uuid") + "/instagram/click")
                        .build()
                okHttpClient.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        println(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                    }
                })

                val openURL = Intent(Intent.ACTION_VIEW)
                openURL.data = Uri.parse(instagramUrl)
                startActivity(context, openURL, null)
            }
        }

        if (youtubeUrl.isNullOrEmpty()) {
            ivAdYoutube.setImageResource(R.drawable.ic_youtube_active)
            ivAdYoutube.isClickable = false
        } else {
            ivAdYoutube.setImageResource(R.drawable.ic_youtube)
            ivAdYoutube.setOnClickListener {
                val okHttpClient = OkHttpClient()
                val request = Request.Builder()
                        .patch(FormBody.Builder().build())
                        .url(rootUrl + "/ads/" + ad.getString("uuid") + "/youtube/click")
                        .build()
                okHttpClient.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        println(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                    }
                })

                val openURL = Intent(Intent.ACTION_VIEW)
                openURL.data = Uri.parse(youtubeUrl)
                startActivity(context, openURL, null)
            }
        }
//        else if (youtubeUrl.isEmpty()) {
//            ivAdYoutube.setImageResource(R.drawable.ic_youtube)
//        }

        if (websiteUrl.isNullOrEmpty()) {
            ivAdWebsite.setImageResource(R.drawable.ic_website)
            ivAdWebsite.isClickable = false
        } else {
            ivAdWebsite.setImageResource(R.drawable.ic_website)
            ivAdWebsite.setOnClickListener {
                val okHttpClient = OkHttpClient()
                val request = Request.Builder()
                        .patch(FormBody.Builder().build())
                        .url(rootUrl + "/ads/" + ad.getString("uuid") + "/website/click")
                        .build()
                okHttpClient.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        println(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                    }
                })

                val openURL = Intent(Intent.ACTION_VIEW)
                openURL.data = Uri.parse(websiteUrl)
                startActivity(context, openURL, null)
            }
        }
        if (telUrl.isNotEmpty()) {
            ivAdTel.setImageResource(R.drawable.ic_phone)
            ivAdTel.setOnClickListener {
                val openURL = Intent(Intent.ACTION_VIEW)
                openURL.data = Uri.parse(telUrl)
                startActivity(context, openURL, null)
            }
        }

        if (whatsAppUrl.isNotEmpty()) {
            ivAdWhatsApp.setImageResource(R.drawable.ic_bigstar)
            ivAdWhatsApp.setOnClickListener {
                val openURL = Intent(Intent.ACTION_VIEW)
                openURL.data = Uri.parse(whatsAppUrl)
                startActivity(context, openURL, null)
            }
        }
    }
}
