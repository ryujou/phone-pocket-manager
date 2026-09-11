import Foundation
import CoreImage
import CoreImage.CIFilterBuiltins
import ImageIO
import UniformTypeIdentifiers

struct PhoneAnalyzer {
    static func thumbnail(_ data: Data) throws -> CGImage {
        guard let source = CGImageSourceCreateWithData(data as CFData,nil), let image = CGImageSourceCreateThumbnailAtIndex(source,0,[kCGImageSourceCreateThumbnailFromImageAlways:true,kCGImageSourceCreateThumbnailWithTransform:true,kCGImageSourceThumbnailMaxPixelSize:1600] as CFDictionary) else { throw PocketError.message("无法读取照片") }
        return image
    }
    static func pixels(_ image: CGImage, width: Int, height: Int) throws -> [UInt8] {
        var buffer = [UInt8](repeating:0,count:width*height*4)
        let ok = buffer.withUnsafeMutableBytes { raw -> Bool in
            guard let ctx = CGContext(data:raw.baseAddress,width:width,height:height,bitsPerComponent:8,bytesPerRow:width*4,space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            ctx.draw(image,in:CGRect(x:0,y:0,width:width,height:height)); return true
        }
        guard ok else { throw PocketError.message("照片转换失败") }; return buffer
    }
    static func masks(_ rgba: [UInt8]) -> ([Bool],[Bool]) {
        var blue=[Bool](), yellow=[Bool](); blue.reserveCapacity(rgba.count/4); yellow.reserveCapacity(rgba.count/4)
        for i in stride(from:0,to:rgba.count,by:4) {
            let r=Double(rgba[i])/255,g=Double(rgba[i+1])/255,b=Double(rgba[i+2])/255
            let hi=max(r,g,b),lo=min(r,g,b),d=hi-lo,s=hi == 0 ? 0 : d/hi
            var h=0.0
            if d>0 { if hi==r { h=60*((g-b)/d).truncatingRemainder(dividingBy:6) } else if hi==g { h=60*((b-r)/d+2) } else { h=60*((r-g)/d+4) }; if h<0 { h+=360 } }
            blue.append(h>=170 && h<=250 && s>=75.0/255 && hi>=45.0/255)
            yellow.append(h>=32 && h<=80 && s>=90.0/255 && hi>=65.0/255)
        }
        return (blue,yellow)
    }
    static func analyze(_ data: Data, corners: [CGPoint]? = nil, rows: [Int]? = nil) throws -> PocketAnalysis {
        let original=try thumbnail(data), width=original.width,height=original.height
        let sourcePixels=try pixels(original,width:width,height:height)
        var points=corners ?? []
        if points.isEmpty {
            let blue=masks(sourcePixels).0
            let smallWidth=(width+1)/2, smallHeight=(height+1)/2
            var visited=[Bool](repeating:false,count:smallWidth*smallHeight), largest=[Int]()
            for index in visited.indices where !visited[index] {
                let x=(index%smallWidth)*2,y=(index/smallWidth)*2
                guard blue[y*width+x] else { continue }
                var queue=[index], head=0; visited[index]=true
                while head<queue.count {
                    let current=queue[head]; head+=1
                    let cx=current%smallWidth,cy=current/smallWidth
                    for (nx,ny) in [(cx-1,cy),(cx+1,cy),(cx,cy-1),(cx,cy+1)] where nx>=0 && nx<smallWidth && ny>=0 && ny<smallHeight {
                        let n=ny*smallWidth+nx
                        if !visited[n] && blue[(ny*2)*width+nx*2] { visited[n]=true; queue.append(n) }
                    }
                }
                if queue.count>largest.count { largest=queue }
            }
            var extremes=[Double.infinity,-Double.infinity,-Double.infinity,Double.infinity]
            points=[CGPoint](repeating:.zero,count:4); var count=0
            for index in largest {
                let x=(index%smallWidth)*2,y=(index/smallWidth)*2
                count+=1; let sum=Double(x+y),diff=Double(x-y),p=CGPoint(x:Double(x)/Double(width),y:Double(y)/Double(height))
                if sum<extremes[0] { extremes[0]=sum; points[0]=p }; if diff>extremes[1] { extremes[1]=diff; points[1]=p }
                if sum>extremes[2] { extremes[2]=sum; points[2]=p }; if diff<extremes[3] { extremes[3]=diff; points[3]=p }
            }
            guard count>width*height/400 else { throw PocketError.message("未找到完整蓝色袋体，请手动选择四角。") }
        }
        guard points.count==4, points.allSatisfy({ (0...1).contains($0.x) && (0...1).contains($0.y) }) else { throw PocketError.message("请按顺序选择四个角点。") }
        var area=0.0
        for i in 0..<4 { let a=points[i], b=points[(i+1)%4],c=points[(i+2)%4]; guard (b.x-a.x)*(c.y-b.y)-(b.y-a.y)*(c.x-b.x)>0.001 else { throw PocketError.message("角点顺序应为左上、右上、右下、左下。") }; area+=a.x*b.y-b.x*a.y }
        guard area/2>0.05 else { throw PocketError.message("选中的袋体过小。") }
        let filter=CIFilter.perspectiveCorrection(); filter.inputImage=CIImage(cgImage:original)
        let vectors=points.map { CGPoint(x:$0.x*Double(width),y:(1-$0.y)*Double(height)) }
        filter.topLeft=vectors[0]; filter.topRight=vectors[1]; filter.bottomRight=vectors[2]; filter.bottomLeft=vectors[3]
        guard let corrected=filter.outputImage else { throw PocketError.message("透视校正失败") }
        let ci=corrected.transformed(by:CGAffineTransform(translationX:-corrected.extent.minX,y:-corrected.extent.minY)).transformed(by:CGAffineTransform(scaleX:600/corrected.extent.width,y:1080/corrected.extent.height))
        guard let image=CIContext().createCGImage(ci,from:CGRect(x:0,y:0,width:600,height:1080)) else { throw PocketError.message("无法生成校正图片") }
        let (blue,yellow)=masks(try pixels(image,width:600,height:1080))
        func fraction(_ mask: [Bool],_ x0:Int,_ x1:Int,_ y0:Int,_ y1:Int) -> Double {
            let a=max(0,y0),b=min(1080,y1); guard b>a else { return 0 }; var n=0
            for y in a..<b { for x in x0..<x1 where mask[y*600+x] { n+=1 } }; return Double(n)/Double((b-a)*(x1-x0))
        }
        let grid=rows ?? (0..<9).map { r in let expected=65+r*114; return (max(8,expected-30)...min(1060,expected+30)).max { fraction(yellow,0,600,$0-4,$0+5)<fraction(yellow,0,600,$1-4,$1+5) } ?? expected }
        guard grid.count==9, grid.allSatisfy({ (5...1070).contains($0) }), zip(grid,grid.dropFirst()).allSatisfy({ $1-$0>=20 }) else { throw PocketError.message("袋口线需按顺序排列且间隔至少20像素。") }
        let clipped=points.contains { $0.x<0.003 || $0.x>0.997 || $0.y<0.003 || $0.y>0.997 }
        var states=[SubmissionState](),scores=[Double]()
        for row in grid { for col in 0..<6 {
            let x=col*100
            let lip=(max(0,row-5)..<min(1080,row+43)).max { fraction(blue,x+14,x+86,$0-1,$0+2)<fraction(blue,x+14,x+86,$1-1,$1+2) } ?? row
            let score=fraction(yellow,x+14,x+86,lip-13,lip-3)
            var state:SubmissionState=score>=0.65 ? .missing : (score<=0.40 ? .submitted : .review)
            if clipped || fraction(blue,x,x+100,row-8,row+30)<0.015 { state = .review }
            states.append(state); scores.append(score)
        }}
        let output=NSMutableData()
        guard let destination=CGImageDestinationCreateWithData(output,UTType.png.identifier as CFString,1,nil) else { throw PocketError.message("图片编码失败") }
        CGImageDestinationAddImage(destination,image,nil); guard CGImageDestinationFinalize(destination) else { throw PocketError.message("图片保存失败") }
        return PocketAnalysis(image:output as Data,corners:points,rows:grid,states:states,scores:scores,clipped:clipped)
    }
}
