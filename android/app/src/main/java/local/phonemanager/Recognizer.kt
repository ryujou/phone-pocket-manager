package local.phonemanager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.*

data class Pocket(val number: String,val prediction: String,val score: Double,val box: IntArray)
data class Analysis(val bitmap: Bitmap,val corners: List<Point>,val rows: List<Int>,val cols: List<Int>,val pockets: List<Pocket>,val warning: String) {
    fun metadata() = JSONObject().put("version","yellow-band-android-v1").put("corners",JSONArray(corners.map { listOf(it.x,it.y) })).put("rows",JSONArray(rows)).put("cols",JSONArray(cols)).put("warning",warning).put("pockets",JSONArray(pockets.map { JSONObject().put("number",it.number).put("prediction",it.prediction).put("score",it.score).put("box",JSONArray(it.box.toList())) }))
    companion object {
        fun restore(bitmap: Bitmap,j: JSONObject) = Analysis(bitmap,j.getJSONArray("corners").let { a -> (0..3).map { Point(a.getJSONArray(it).getDouble(0),a.getJSONArray(it).getDouble(1)) } },j.getJSONArray("rows").let { a -> (0 until a.length()).map { a.getInt(it) } },j.getJSONArray("cols").let { a -> (0 until a.length()).map { a.getInt(it) } },j.getJSONArray("pockets").objects().map { p -> Pocket(p.getString("number"),p.getString("prediction"),p.getDouble("score"),IntArray(4) { p.getJSONArray("box").getInt(it) }) },j.text("warning"))
    }
}
object Recognizer {
    const val W=600; const val H=1080
    fun load(file: File): Bitmap {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeFile(file.path,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0) { "无法读取照片" }
        var factor=1; while(max(bounds.outWidth,bounds.outHeight)/factor>4000) factor*=2
        val original=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=factor }) ?: error("照片解码失败")
        val matrix=Matrix()
        when(ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)) {
            2 -> matrix.setScale(-1f,1f); 3 -> matrix.setRotate(180f); 4 -> matrix.setScale(1f,-1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f,1f) }; 6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(-90f); matrix.postScale(-1f,1f) }; 8 -> matrix.setRotate(-90f)
        }
        val rotated=Bitmap.createBitmap(original,0,0,original.width,original.height,matrix,true)
        if(rotated!==original) original.recycle()
        val scale=min(1.0,min(1600.0/rotated.width,2000.0/rotated.height))
        val scaled=Bitmap.createScaledBitmap(rotated,(rotated.width*scale).roundToInt(),(rotated.height*scale).roundToInt(),true)
        if(scaled!==rotated) rotated.recycle(); return scaled
    }
    fun recognize(bitmap: Bitmap, manual: List<Point>?=null, rowOverride: List<Int>?=null, colOverride: List<Int>?=null): Analysis {
        check(OpenCVLoader.initLocal()) { "OpenCV初始化失败" }
        val mats=mutableListOf<Mat>()
        fun mat()=Mat().also { mats.add(it) }
        fun track(m: Mat)=m.also { mats.add(it) }
        try {
            val rgba=mat(); Utils.bitmapToMat(bitmap,rgba)
            val rgb=mat(); Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB)
            fun masks(input: Mat): Pair<Mat,Mat> {
                val hsv=mat(); Imgproc.cvtColor(input,hsv,Imgproc.COLOR_RGB2HSV)
                val b=mat(); val y=mat(); Core.inRange(hsv,Scalar(85.0,75.0,45.0),Scalar(125.0,255.0,255.0),b); Core.inRange(hsv,Scalar(16.0,90.0,65.0),Scalar(40.0,255.0,255.0),y); return b to y
            }
            val corners=manual ?: run {
                val (blue,_)=masks(rgb); val closed=mat()
                Imgproc.morphologyEx(blue,closed,Imgproc.MORPH_CLOSE,track(Mat.ones(9,9,CvType.CV_8U)))
                val contours=mutableListOf<MatOfPoint>(); Imgproc.findContours(closed,contours,mat(),Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE); mats.addAll(contours)
                val biggest=contours.maxByOrNull { Imgproc.contourArea(it) } ?: error("未找到蓝色袋体，请手动点击四角")
                require(Imgproc.contourArea(biggest)>=bitmap.width*bitmap.height*.12) { "袋体太小或不完整，请重拍或标定四角" }
                val indices=MatOfInt(); track(indices); Imgproc.convexHull(biggest,indices)
                val all=biggest.toArray(); val hull=indices.toArray().map { all[it] }
                val curve=MatOfPoint2f(*hull.toTypedArray()); track(curve); val approx=MatOfPoint2f(); track(approx)
                Imgproc.approxPolyDP(curve,approx,.025*Imgproc.arcLength(curve,true),true)
                val p=if(approx.total()==4L) approx.toList() else hull
                listOf(p.minBy { it.x+it.y },p.minBy { it.y-it.x },p.maxBy { it.x+it.y },p.maxBy { it.y-it.x })
            }
            require(corners.size==4 && corners.distinct().size==4) { "需要四个不同的角点" }
            val polygon=MatOfPoint(*corners.toTypedArray()); track(polygon)
            require(Imgproc.isContourConvex(polygon) && Imgproc.contourArea(polygon)>=bitmap.width*bitmap.height*.05 && corners.all { it.x>=0 && it.y>=0 && it.x<bitmap.width && it.y<bitmap.height }) { "按左上、右上、右下、左下标定完整袋体" }
            val src=MatOfPoint2f(*corners.toTypedArray()); track(src)
            val dst=MatOfPoint2f(Point(0.0,0.0),Point(599.0,0.0),Point(599.0,1079.0),Point(0.0,1079.0)); track(dst)
            val trans=track(Imgproc.getPerspectiveTransform(src,dst)); val warped=mat(); Imgproc.warpPerspective(rgb,warped,trans,Size(W.toDouble(),H.toDouble()))
            val (b,y)=masks(warped); val blue=ByteArray(W*H); val yellow=ByteArray(W*H); val pixels=ByteArray(W*H*3)
            b.get(0,0,blue); y.get(0,0,yellow); warped.get(0,0,pixels)
            fun fraction(a: ByteArray,x0: Int,x1: Int,y0: Int,y1: Int): Double {
                var count=0; for(yy in y0 until y1) for(x in x0 until x1) if(a[yy*W+x].toInt()!=0) count++
                return count.toDouble()/((x1-x0)*(y1-y0)).coerceAtLeast(1)
            }
            fun smooth(a: List<Double>,n: Int)=a.indices.map { i -> (i-n/2..i+n/2).sumOf { a.getOrElse(it){0.0} }/n }
            val projection=smooth((0 until H).map { fraction(yellow,0,W,it,it+1) },9)
            val rows=rowOverride ?: (0..8).map { i -> val expected=65+i*114; (max(8,expected-30) until min(H-20,expected+31)).maxBy { projection[it] } }
            val cols=colOverride ?: (0..6).map { it*100 }
            require(rows.size==9 && cols.size==7 && rows.zipWithNext().all { it.second-it.first>=20 } && cols.zipWithNext().all { it.second-it.first>=20 } && rows.first()>=5 && rows.last()<=H-10 && cols.first()>=0 && cols.last()<=W) { "请输入9条递增行线、7条递增列线，每条间隔至少20像素" }
            val clipped=corners.any { it.x<=2 || it.y<=2 || it.x>=bitmap.width-3 || it.y>=bitmap.height-3 }
            val pockets=rows.flatMapIndexed { r,center -> (0..5).map { c ->
                val left=cols[c]; val right=cols[c+1]; val pad=((right-left)*.14).toInt(); val x0=left+pad; val x1=right-pad
                val ba=max(0,center-5); val bb=min(H,center+43)
                val smooth=smooth((ba until bb).map { fraction(blue,x0,x1,it,it+1) },3)
                val lip=ba+smooth.indices.maxBy { smooth[it] }
                val score=fraction(yellow,x0,x1,max(0,lip-13),max(1,lip-3))
                val local=fraction(blue,left,right,max(0,center-8),min(H,center+30))
                val box=intArrayOf(x0,max(0,center-65),x1,min(H,center+28))
                var sum=0.0; var sq=0.0; var n=0
                for(yy in box[1] until box[3]) for(x in x0 until x1) for(ch in 0..2) { val v=pixels[(yy*W+x)*3+ch].toInt() and 255; sum+=v; sq+=v*v; n++ }
                val low=sqrt(max(0.0,sq/n-(sum/n).pow(2)))<8
                val state=if(clipped || local<.015 || low) "review" else if(score>=.65) "empty" else if(score<=.40) "phone" else "review"
                Pocket("%02d".format(r*6+c+1),state,score,box)
            } }
            val output=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); Utils.matToBitmap(warped,output)
            return Analysis(output,corners,rows,cols,pockets,if(clipped) "袋体贴近图片边缘，可能未拍全，本次全部待复核" else "")
        } finally { mats.forEach { it.release() } }
    }
}
