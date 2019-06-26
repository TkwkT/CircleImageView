package com.example.circle


import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL


class CircleImageView : View {
    private var bitmap: Bitmap? = null
    private var mPaint: Paint? = null
    private var resId: Int = 0
    private var isSetBitmap: Boolean = false
    private var srcUrl: String? = null

    private var duffXfermode: PorterDuffXfermode? = null
    val bitmapSize: Int
        get() {
            var size = 0
            if (resId != 0) {
                val options = BitmapFactory.Options()
                BitmapFactory.decodeResource(resources, resId, options)
                val height = options.outHeight
                val width = options.outWidth
                size = Math.min(height, width)
            }
            return size
        }

    private val resultSize: Int
        get() {
            val size = measuredHeight
            return Math.min(size - paddingLeft - paddingRight, size - paddingTop - paddingBottom)
        }

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        val array = context.obtainStyledAttributes(attrs, R.styleable.CircleImageView)
        resId = array.getResourceId(R.styleable.CircleImageView_src, 0)
        initPaint()
        array.recycle()
    }

    private fun initPaint() {
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mPaint!!.isDither = true
        mPaint!!.isFilterBitmap = true
        duffXfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    private fun makeCircle(): Bitmap {
        val paddingLeft = paddingLeft
        val paddingTop = paddingTop
        val resultSize = this.resultSize
        val circleBitmap = Bitmap.createBitmap(resultSize, resultSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(circleBitmap)
        val paint = Paint()
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(
            (resultSize / 2 + paddingLeft).toFloat(),
            (resultSize / 2 + paddingTop).toFloat(),
            (resultSize / 2).toFloat(),
            paint
        )
        return circleBitmap
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val size: Int
        size = if (widthMode == heightMode && widthMode == MeasureSpec.AT_MOST) {
            bitmapSize
        } else if (widthMode == MeasureSpec.AT_MOST) {
            height
        } else if (heightMode == MeasureSpec.AT_MOST) {
            width
        } else {
            Math.min(height, width)
        }
        setMeasuredDimension(size, size)
    }

    fun setBitmap(bitmap: Bitmap) {
        this.bitmap = bitmap
        isSetBitmap = true
        invalidate()
    }

    fun setBitmapFromUrl(url: String) {
        this.srcUrl = url
        getInputStream(url){
            try {
                val resultSize = resultSize
                val bitmap1 = decodeBitmapFromInputStream(it, resultSize, resultSize)
                post {
                    bitmap = bitmap1
                    invalidate()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setBitmapFromResource(resId: Int) {
        this.resId = resId
        invalidate()
    }

    private fun getBitmap(): Bitmap? {
        val resultSize = resultSize
        if (!isSetBitmap && srcUrl == null) {
            bitmap = decodeSampledBitmapFromResource(resources, resId, resultSize, resultSize)
        }
        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val resultSize = resultSize
        val sc = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), mPaint, Canvas.ALL_SAVE_FLAG)
        val de = makeCircle()
        canvas.drawBitmap(de, 0f, 0f, mPaint)
        bitmap = getBitmap()
        if (bitmap != null) {
            mPaint!!.xfermode = duffXfermode
            bitmap = zooImage(bitmap!!, resultSize, resultSize)
            canvas.drawBitmap(bitmap!!, paddingLeft.toFloat(), paddingTop.toFloat(), mPaint)
            mPaint!!.xfermode = null
        }
        canvas.restoreToCount(sc)
    }

    /*
     *缩放图片
     */
    private fun zooImage(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val size = Math.min(width, height)
        val scaleWidth = newWidth.toFloat() / size
        val scaleHeight = newHeight.toFloat() / size
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        return Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true)
    }

    private fun decodeSampledBitmapFromResource(res: Resources, resId: Int, reqWidth: Int, reqHeight: Int): Bitmap {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeResource(res, resId, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(res, resId, options)
    }

    private fun decodeBitmapFromInputStream(inputStream: InputStream?, reqWidth: Int, reqHeight: Int): Bitmap? {
        val input = BufferedInputStream(inputStream) //BufferedInputStream 支持来回读写操作
        input.mark(Integer.MAX_VALUE)//标记流的初始位置
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(input, null, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        try {
            input.reset() //重置读写时流的初始位置
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return BitmapFactory.decodeStream(input, null, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        if (reqHeight == 0 || reqWidth == 0) {
            return 1
        }
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize > reqHeight && halfWidth / inSampleSize > reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {

        //从网络获取输入流
        fun getInputStream(address: String, listener:(inputStream: InputStream?) -> Unit ) {
            Thread(Runnable {
                var inputStream: InputStream? = null
                var connection: HttpURLConnection? = null
                try {
                    val url = URL(address)
                    connection = url.openConnection() as HttpURLConnection
                    connection.doInput = true
                    connection.requestMethod = "GET"
                    connection.readTimeout = 8000
                    connection.connectTimeout = 8000
                    inputStream = connection.inputStream
                    listener(inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    connection?.disconnect()
                    if (inputStream != null)
                        try {
                            inputStream.close()
                        } catch (i: IOException) {
                            i.printStackTrace()
                        }
                }
            }).start()
        }
    }
}
