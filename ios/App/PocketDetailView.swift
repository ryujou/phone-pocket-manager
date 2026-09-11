import SwiftUI
struct PocketDetailView:View {
    let record:Record
    let analysis:PocketAnalysis?
    var body:some View {
        NavigationStack { VStack(spacing:20) {
            if let analysis,let image=UIImage(data:analysis.image)?.cgImage,let index=Int(record.pocket), (1...54).contains(index) {
                let row=analysis.rows[(index-1)/6],x=((index-1)%6)*100
                if let crop=image.cropping(to:CGRect(x:x,y:max(0,row-65),width:100,height:93)) { Image(decorative:crop,scale:1).resizable().scaledToFit().frame(maxHeight:350) }
            }
            Text("初筛：\(record.prediction.rawValue)")
            Text("黄色露出评分 \(record.score,format:.number.precision(.fractionLength(2)))，不是置信概率。").font(.caption).foregroundStyle(.secondary)
            Text("关闭后可在袋位卡片修改状态。")
        }.padding().navigationTitle("\(record.pocket) · \(record.name)") }.presentationDetents([.medium,.large])
    }
}
