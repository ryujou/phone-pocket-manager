import AppKit
import WebKit

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate, WKNavigationDelegate, WKUIDelegate, WKDownloadDelegate {
    var project: String { Bundle.main.bundleURL.deletingLastPathComponent().appendingPathComponent("desktop").path }
    var python: String { project + "/.venv/bin/python" }
    let address = URL(string: "http://127.0.0.1:8501")!
    var window: NSWindow!
    var web: WKWebView!
    var destinations: [ObjectIdentifier: (URL, URL)] = [:]
    func applicationDidFinishLaunching(_ notification: Notification) {
        if let iconURL = Bundle.main.url(forResource: "AppIcon", withExtension: "icns"),
           let icon = NSImage(contentsOf: iconURL) {
            NSApp.applicationIconImage = icon
        }
        let menu = NSMenu()
        let item = NSMenuItem(); menu.addItem(item)
        let main = NSMenu(); item.submenu = main
        main.addItem(withTitle: "退出手机上交管理", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        let edit = NSMenuItem(); menu.addItem(edit); let em = NSMenu(title: "编辑"); edit.submenu = em
        for (name, selector, key) in [("剪切", "cut:", "x"), ("复制", "copy:", "c"), ("粘贴", "paste:", "v"), ("全选", "selectAll:", "a")] {
            em.addItem(withTitle: name, action: Selector(selector), keyEquivalent: key)
        }
        let viewItem = NSMenuItem(); menu.addItem(viewItem); let vm = NSMenu(title: "窗口"); viewItem.submenu = vm
        let reload = vm.addItem(withTitle: "重新载入", action: #selector(reloadPage), keyEquivalent: "r"); reload.target = self
        NSApp.mainMenu = menu
        window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1400, height: 900), styleMask: [.titled,.closable,.miniaturizable,.resizable], backing: .buffered, defer: false)
        window.title = "手机上交管理"; window.minSize = NSSize(width: 1000, height: 700)
        window.isReleasedWhenClosed = false; window.center(); window.setFrameAutosaveName("PhoneManagerMain")
        web = WKWebView(frame: .zero, configuration: WKWebViewConfiguration())
        web.navigationDelegate = self; web.uiDelegate = self
        window.contentView = web; window.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true)
        web.loadHTMLString("<html><meta charset='utf-8'><body style='font:22px system-ui;padding:80px;color:#087f8c'>正在启动手机上交管理…</body></html>", baseURL: nil)
        Task { await start() }
    }
    @objc func reloadPage() { web.load(URLRequest(url: address)) }
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool { window.makeKeyAndOrderFront(nil); return true }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
    func healthy() async -> Bool {
        var req = URLRequest(url: address.appendingPathComponent("_stcore/health")); req.timeoutInterval = 1
        guard let (data, response) = try? await URLSession.shared.data(for: req), (response as? HTTPURLResponse)?.statusCode == 200 else { return false }
        return String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) == "ok"
    }
    func alert(_ text: String) { let a = NSAlert(); a.messageText = "手机上交管理"; a.informativeText = text; a.beginSheetModal(for: window) }
    func start() async {
        if !(await healthy()) {
            guard FileManager.default.isExecutableFile(atPath: python), FileManager.default.fileExists(atPath: project + "/app.py") else { alert("找不到原项目或运行环境，请保留原项目目录。\n" + project); return }
            let logs = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/Logs/PhoneManager")
            try? FileManager.default.createDirectory(at: logs, withIntermediateDirectories: true)
            let log = logs.appendingPathComponent("server.log")
            if !FileManager.default.fileExists(atPath: log.path) { FileManager.default.createFile(atPath: log.path, contents: nil) }
            let p = Process(); p.executableURL = URL(fileURLWithPath: "/usr/bin/nohup")
            p.arguments = [python,"-m","streamlit","run","app.py","--server.address","127.0.0.1","--server.port","8501","--server.headless","true","--browser.gatherUsageStats","false"]
            p.currentDirectoryURL = URL(fileURLWithPath: project); p.standardInput = FileHandle.nullDevice
            let output = try? FileHandle(forWritingTo: log); _ = try? output?.seekToEnd()
            p.standardOutput = output ?? FileHandle.nullDevice; p.standardError = output ?? FileHandle.nullDevice
            do { try p.run() } catch { alert(error.localizedDescription); return }
            var ready = false
            for _ in 0..<40 { if await healthy() { ready = true; break }; try? await Task.sleep(nanoseconds: 500_000_000) }
            if !ready { alert("服务未就绪，请查看日志：\n" + log.path); return }
        }
        reloadPage()
    }
    func webView(_ webView: WKWebView, runOpenPanelWith parameters: WKOpenPanelParameters, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping ([URL]?) -> Void) {
        let panel = NSOpenPanel(); panel.canChooseFiles = true; panel.canChooseDirectories = false; panel.allowsMultipleSelection = parameters.allowsMultipleSelection
        panel.beginSheetModal(for: window) { response in completionHandler(response == .OK ? panel.urls : nil) }
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if navigationAction.shouldPerformDownload { decisionHandler(.download); return }
        if let url = navigationAction.request.url, !["127.0.0.1","localhost"].contains(url.host ?? ""), !["about","blob","data"].contains(url.scheme ?? "") { decisionHandler(.cancel); return }
        decisionHandler(.allow)
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        let attachment = (navigationResponse.response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Disposition")?.lowercased().contains("attachment") ?? false
        decisionHandler(attachment || !navigationResponse.canShowMIMEType ? .download : .allow)
    }
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if navigationAction.targetFrame == nil { webView.load(navigationAction.request) }; return nil
    }
    func webView(_ webView: WKWebView, navigationAction: WKNavigationAction, didBecome download: WKDownload) { download.delegate = self }
    func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) { download.delegate = self }
    func download(_ download: WKDownload, decideDestinationUsing response: URLResponse, suggestedFilename: String, completionHandler: @escaping (URL?) -> Void) {
        let panel = NSSavePanel(); panel.canCreateDirectories = true; panel.directoryURL = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first; panel.nameFieldStringValue = (suggestedFilename as NSString).lastPathComponent
        panel.beginSheetModal(for: window) { [self] result in
            guard result == .OK, let destination = panel.url else { completionHandler(nil); return }
            let temporary = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            destinations[ObjectIdentifier(download)] = (temporary, destination); completionHandler(temporary)
        }
    }
    func downloadDidFinish(_ download: WKDownload) {
        guard let (temp, dest) = destinations.removeValue(forKey: ObjectIdentifier(download)) else { return }
        do {
            if FileManager.default.fileExists(atPath: dest.path) { _ = try FileManager.default.replaceItemAt(dest, withItemAt: temp) }
            else { try FileManager.default.moveItem(at: temp, to: dest) }
        } catch { alert("保存失败：" + error.localizedDescription + "\n已下载文件暂存于：" + temp.path) }
    }
    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        if let (temp, _) = destinations.removeValue(forKey: ObjectIdentifier(download)) { try? FileManager.default.removeItem(at: temp) }
        if (error as NSError).code != NSURLErrorCancelled { alert("下载失败：" + error.localizedDescription) }
    }
}
let app = NSApplication.shared
app.setActivationPolicy(.regular)
let delegate = MainActor.assumeIsolated { AppDelegate() }; app.delegate = delegate
app.run()
