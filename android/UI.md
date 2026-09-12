# Android Material 3 界面

Android 1.1 使用 Google 官方 Material Components 1.13.0 的 Material 3 DayNight 主题。保留 Kotlin ViewModel、SQLite 和 OpenCV 业务实现，替换界面组件，不需要迁移数据库。

- MaterialToolbar 顶部栏与四项 BottomNavigationView 底部导航。
- 拍照与相册入口、课次表单采用独立圆角卡片。
- MaterialButton、TextInputLayout、MaterialCheckBox 和 Material 对话框统一交互。
- 姓名核对卡片同时显示文字状态与颜色；放大图片提供无障碍说明。
- Android 12+ 支持系统动态配色；旧系统使用青绿色品牌色。
- 跟随系统浅色/深色模式，采用分别适配的上交、未交和待复核颜色。
- 输入框、状态栏、导航栏和键盘区域保留安全间距。

依赖变更：`com.google.android.material:material:1.13.0`。应用界面改为 `AppCompatActivity`，未改动数据存储格式。

开发资料：[Google Material Components](https://github.com/material-components/material-components-android)。

验证命令：

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
# 连接设备或启动模拟器后
./gradlew connectedDebugAndroidTest
```

公开版包名为 `org.phonepocketmanager.app`，仅附虚构数据。个人安装包保持原包名，使用原签名，不作为公开资源上传。

## 1.1 验证记录

2026-09-12：公开版 Debug 构建通过，8 项 JVM 测试通过；Android 15 / Pixel 7 模拟器上的 4 项 instrumentation 测试通过，覆盖四栏导航、深色模式页面、名单与识别接口、重复保存及更正后的统计。个人版 Release 构建通过。

自动测试验证组件可见及业务行为，不代替人工视觉检查。此次开发机器锁屏，尚未完成模拟器视觉检查和 Android 界面截图；系统相机拍摄仍需真机验证。
