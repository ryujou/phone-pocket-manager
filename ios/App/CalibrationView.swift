import SwiftUI
struct CalibrationView:View {
    let data:Data
    let previous:PocketAnalysis?
    var apply:([CGPoint]?,[Int]?)->Void
    @Environment(\.dismiss) private var dismiss
    @State private var points:[CGPoint]=[]
    @State private var lines=""
    var body:some View {
        NavigationStack { ScrollView { VStack(alignment:.leading,spacing:16) {
            Text("依次点击左上、右上、右下、左下，包含顶部横条和最底部。已选 \(points.count)/4 个角点。")
            if let cg=try? PhoneAnalyzer.thumbnail(data) {
                Image(decorative:cg,scale:1).resizable().scaledToFit().overlay { GeometryReader { geo in
                    ForEach(Array(points.enumerated()),id:\.offset) { i,p in Text("\(i+1)").font(.headline).padding(6).background(.teal,in:.circle).foregroundStyle(.white).position(x:p.x*geo.size.width,y:p.y*geo.size.height) }
                    Color.clear.contentShape(.rect).onTapGesture { p in if points.count<4 { points.append(CGPoint(x:p.x/geo.size.width,y:p.y/geo.size.height)) } }
                }}.accessibilityLabel("手动四角标定图片")
            }
            Button("重新选点") { points=[] }
            Text("袋口线（可选）").font(.headline)
            TextField("9 个像素值，逗号分隔",text:$lines,axis:.vertical).textFieldStyle(.roundedBorder)
            Text("校正图高 1080 像素。留空自动定位；修改会重置未保存的核对结果。列固定为六等分。").font(.caption).foregroundStyle(.secondary)
            Button("应用并重新识别") {
                let rows=lines.trimmingCharacters(in:.whitespaces).isEmpty ? nil : lines.replacingOccurrences(of:"，",with:",").split(separator:",").map { Int($0.trimmingCharacters(in:.whitespaces)) ?? -1 }
                apply(points.isEmpty ? previous?.corners : points,rows); dismiss()
            }.buttonStyle(.borderedProminent).disabled(!points.isEmpty && points.count != 4)
        }.padding() }.navigationTitle("校正袋位").toolbar { Button("取消") { dismiss() } } }
    }
}
