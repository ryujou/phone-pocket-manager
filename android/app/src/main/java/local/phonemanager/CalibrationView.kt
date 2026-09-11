package local.phonemanager

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import org.opencv.core.Point

class CalibrationView(context: Context,val bitmap: Bitmap): View(context) {
    val points=mutableListOf<Point>()
    var changed: ((Int)->Unit)?=null
    private val rect=RectF()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onMeasure(w: Int,h: Int) { val width=MeasureSpec.getSize(w); setMeasuredDimension(width,(width*bitmap.height.toFloat()/bitmap.width).toInt().coerceAtMost((resources.displayMetrics.heightPixels*.60).toInt())) }
    override fun onDraw(c: Canvas) {
        val scale=minOf(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
        val x=(width-bitmap.width*scale)/2; val y=(height-bitmap.height*scale)/2
        rect.set(x,y,x+bitmap.width*scale,y+bitmap.height*scale); c.drawBitmap(bitmap,null,rect,null)
        paint.color=Color.rgb(255,180,20); paint.strokeWidth=5f
        points.forEachIndexed { i,p ->
            val px=x+p.x.toFloat()*scale; val py=y+p.y.toFloat()*scale
            c.drawCircle(px,py,9f,paint); paint.textSize=30f; c.drawText("${i+1}",px+10,py+10,paint)
            if(i>0) { val a=points[i-1]; c.drawLine(x+a.x.toFloat()*scale,y+a.y.toFloat()*scale,px,py,paint) }
        }
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(e.action==MotionEvent.ACTION_UP && rect.contains(e.x,e.y)) {
            if(points.size==4) points.clear()
            points.add(Point(((e.x-rect.left)/rect.width()*bitmap.width).toDouble(),((e.y-rect.top)/rect.height()*bitmap.height).toDouble()))
            invalidate(); changed?.invoke(points.size); performClick()
        }; return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
