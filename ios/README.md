# iOS 版 · 手机上交管理

原生 SwiftUI 应用，按 Mac 版的“照片 → 核对 → 课次 → 统计”流程实现。支持 iPhone / iPad，最低 iOS 26，使用 Xcode 26 编译。

## 打开与安装

打开 `PhonePocketManager.xcodeproj`，选择 `PhonePocketManager` Scheme 和 iPhone 模拟器，点击 Run。

安装到自己的 iPhone：在 **Signing & Capabilities** 中选择自己的 Team，按需要修改 Bundle Identifier，连接设备并 Run。仓库不包含开发者证书或签名密钥。未签名的模拟器 `.app` 不能直接安装到真机；本版没有发布 TestFlight 或 App Store。

```sh
xcodebuild -project ios/PhonePocketManager.xcodeproj \
  -scheme PhonePocketManager -sdk iphonesimulator \
  -configuration Debug -derivedDataPath /tmp/phone-pocket-ios \
  CODE_SIGNING_ALLOWED=NO build
swift test --package-path ios
```

以上命令在仓库根目录执行。

## 功能

- 拍照、系统相册导入、内置 AI 修改示例图。
- 蓝黄手机袋自动四角定位、透视校正、9 × 6 袋位初筛；支持手动选四角和输入袋口行线，列固定六等分。
- 卡片显示袋号与学生姓名，菜单修改状态，点击姓名放大袋口。
- XLSX 多班导入与预览，转班学生可修改当前班级并保留原班级备注；同班学号/袋号冲突会阻止导入。
- 编辑学生姓名和袋号；历史课次保留原姓名和袋号快照。
- 重复保存同班、学期、日期、课次时更新记录，不重复累计；名单集合变化时引导到历史更正，避免覆盖旧课次。
- 历史状态和备注可修改，原预测与修改日志保留。
- 按班级、学期、日期统计，生成 XLSX 汇总和明细，通过系统分享保存到“文件”。

## 数据与兼容范围

演示空间只预置虚构名单，正式空间初始为空。数据保存在应用沙盒的 Application Support 下，照片与记录不发送到服务器。iOS 使用原子写入的 Codable JSON 保存记录，独立于 Mac / Android 的 SQLite；暂不提供跨端同步或直接导入桌面数据库。损坏的数据文件会阻止保存，避免静默覆盖。

初次启动演示班为六人；`Resources/roster_2.xlsx` 是第二个虚构班级的导入模板。真实资料不要提交到源码仓库。

XLSX 支持常见共享字符串、内联文本和标准 ZIP Deflate 工作簿，不支持加密、宏及计算公式；公式使用文件内已有缓存值。学号列应使用文本，若 Excel 已将前导零丢失，导入无法恢复。文件大小上限 30 MB，解压总大小上限 50 MB。导出使用文本单元格，避免备注被当作公式执行。

## 识别与限制

用 Apple Core Image 和 Swift 复现 Mac 版的颜色/袋口规则，无第三方运行时依赖，不使用云端模型。自动定位采用最大蓝色连通区域的像素极值，复杂背景下应手动校正。袋体未拍全或局部信息不足时标为待复核。识别质量不等同于 Mac OpenCV 版，不承诺现场准确率。

应先选择班级、照片和课次，再逐人核对；切换班级或照片会重置未保存结果。首版需手动保存，离开应用前应确认记录已经保存。卸载前导出统计；目前没有完整数据库恢复界面。

相机必须使用真机验证，模拟器提供示例/相册路径。相机采用系统 UIImagePickerController，由 SwiftUI 包装调用；相册使用 PhotosPicker。

## 本次验证

- 9 项 Swift 核心/图像测试通过：保存去重、历史快照、冲突原子性、前导零、ZIP 损坏检测、空间隔离、合成袋位与边界待复核。
- iPhone 17 Pro / iOS 26.5 模拟器：样图识别、6 人姓名映射、确认保存、历史可见、统计和 Excel 生成通过。
- 实际导出的 XLSX 用 openpyxl 独立读取：两个工作表、6 名学生、文本学号、每人一次记录一致。
- 模拟器 Debug 和未签名真机 Release 编译通过。真机安装、权限拒绝、相机实拍及完整相册/文件选择器流程尚未实机验收。
