import SwiftUI
struct PocketCard:View {
    @Binding var record:Record
    var inspect:()->Void
    private var color:Color { switch record.state { case .submitted:.green; case .missing:.red; case .review:.orange; default:.gray } }
    var body:some View {
        VStack(alignment:.leading,spacing:8) {
            Button(action:inspect) { Text("\(record.pocket) \(record.name)").font(.headline).foregroundStyle(.primary).frame(maxWidth:.infinity,alignment:.leading) }.accessibilityLabel("放大 \(record.pocket) 号 \(record.name) 袋口")
            Picker("状态",selection:$record.state) { ForEach(SubmissionState.allCases) { Text($0.rawValue).tag($0) } }.labelsHidden().tint(color)
        }.padding(10).frame(maxWidth:.infinity,minHeight:90).background(color.opacity(0.12),in:.rect(cornerRadius:14))
    }
}
