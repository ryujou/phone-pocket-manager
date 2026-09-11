import SwiftUI
@main
struct PhonePocketApp: App {
    @State private var model = AppModel()
    var body: some Scene { WindowGroup { RootView().environment(model).tint(.teal) } }
}
