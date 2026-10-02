# LLLLLwjgl3-1.8.9-Forge

一个面向 Minecraft 1.8.9 Forge 的客户端 Mod，用 LWJGL3 + GLFW 替换原版
LWJGL2 运行时。Minecraft/Forge 客户端字节码中的 LWJGL2 符号会在加载时
重写到独立的 `org.lwjglx` API；OpenGL 调用直接使用 LWJGL3 的
`org.lwjgl.opengl.GL*`，窗口、键鼠和 OpenAL 则由 GLFW/LWJGL3 实现。

## 特性

- Forge `1.8.9-11.15.1.2318` 标准 `mods` 安装方式。
- 启动早期将当前 fat JAR 前置到 LaunchWrapper 的实际 URLClassPath loader
  列表，再解除 `org.lwjgl.*` 父类加载隔离，并在
  `Lwjgl3ClassTransformer` 中重写类、字段、方法描述符、异常和指令操作数。
- 直接 vendored `Zarzelcow/legacy-lwjgl3` 兼容层源码到 `org.lwjglx` 命名空间；最终
  Forge JAR 不包含 `org.lwjgl.input.Keyboard`、`org.lwjgl.opengl.Display` 等
  LWJGL2 类。
- `org.lwjglx.openal` 是源码形式的 OpenAL 兼容 API，直接桥接 LWJGL3
  `ALC10/AL10`，不再修改 LWJGL3 类字节码。
- 打包 Linux、Windows、macOS x64 natives；GLFW 原生库包含 Wayland 和
  X11 后端，`lllllwjgl3.backend` 可选择后端。
- 在 Wayland 会话的 `AUTO` 模式下，若存在 `DISPLAY`，默认选择 XWayland
  GLFW X11 backend。这样 Fcitx5/IBus 的 XIM committed text 会通过 GLFW
  `char` callback 进入 `org.lwjglx.input.Keyboard`，再进入 Minecraft 的
  `Keyboard.getEventCharacter()`。
- 可用 `-Dlllllwjgl3.xim=false` 关闭 IME 兼容路径，或用
  `-Dlllllwjgl3.waylandIme=native` 强制原生 Wayland（此模式不承诺 GLFW
  3.3 的 Fcitx5 preedit/commit 支持）。

## 上游代码说明

LWJGL2 兼容层直接 vendored 自
[Zarzelcow/legacy-lwjgl3](https://github.com/Zarzelcow/legacy-lwjgl3)，来源提交为
`78643b2a9621ab04d13e3ec0a222874d793516d2`。对应代码位于
`src/main/java/org/lwjglx/` 和 `src/main/java/com/github/zarzelcow/legacylwjgl3/`，
不是运行时下载的依赖，也不是构建时嵌入的二进制 JAR。

为适配 Forge 1.8.9，本项目对上游代码做了以下本地改造：

- 将上游 `org.lwjgl` 兼容 API 移入 `org.lwjglx` 命名空间，并将窗口实现接入本项目的 `DisplayCompat` 和诊断层。
- 将 GLFW 鼠标坐标、捕获/释放、滚轮单位和销毁重建状态统一到 LWJGL2 语义。
- 在源码层合并 GLFW key/char 回调，保持创造模式搜索、中文输入和 Unicode 输入行为。
- 将旧版 OpenGL buffer overload 在 `Lwjgl3ClassTransformer` 中改写到 LWJGL3 原生方法或 `Lwjgl3ApiCompat`，不再使用 JarJar、二进制 facade 或独立 ASM JAR patcher。

上游兼容层按 LGPL-2.1 发布；许可证和第三方组件说明见
`THIRD_PARTY_NOTICES.md` 与 `licenses/`。

## 安装

1. 安装 Forge `1.8.9-11.15.1.2318`，使用 Java 8。
2. 执行 `./gradlew build`。
3. 将 `build/libs/LLLLLwjgl3-1.8.9-Forge-1.0.1.jar` 放入实例的 `mods`。
4. 启动普通 Forge 客户端，不要再安装其他 LWJGL2 替换 Mod。

Wayland 会在 `XDG_SESSION_TYPE=wayland` 或 `WAYLAND_DISPLAY` 存在时被识别。
为保证中文输入，`AUTO` 会优先使用 XWayland；也可以显式指定：

```text
-Dlllllwjgl3.backend=wayland
-Dlllllwjgl3.backend=x11
-Dlllllwjgl3.waylandIme=native
```

XIM 模式下 GLFW 会把按键和提交文本拆成两个事件，文本事件的 `key=0`。
原版 `Minecraft.dispatchKeypresses()` 会把这类事件的字符直接当作键码，
导致 `Shift+W`（`'W'`=87=`KEY_F11`）切换全屏、`Shift+X`（88=`KEY_F12`）截图。
转换器只把该方法中 `key == 0 ? character : key` 的字符分支改为 0；
`runTick()` 中 `character + 256` 的按键绑定逻辑保持不变。启动日志应出现
`[LLLLLwjgl3] ignored character-only shortcut dispatch`。

启动早期若发生异常，mod 默认会跳过 Minecraft 的 OpenGL 显卡信息采集，避免
LWJGL3 在尚未建立 current context 时通过 native `glGetString` 直接终止 JVM，
从而保留真正的根因堆栈。启动时应看到 `[LLLLLwjgl3] neutralized Minecraft
crash-GL query`；若看到 `WARN: neutralizer found NO target`，说明类映射链
仍未匹配，需要保留该日志进行诊断。确认问题后可用 `-Dlllllwjgl3.safeCrashReport=false`
恢复原版显卡信息采集。

## 构建与验证

诊断桌面快捷键、窗口焦点或鼠标捕获问题时，可以临时添加
`-Dlllllwjgl3.inputDiagnostics=true`，或直接使用
`MC_ROOT=... MC_VERSION=... LWJGL3_INPUT_DIAGNOSTICS=true ./run-smoke-test.sh`。日志前缀为
`[LLLLLwjgl3/input]`，记录 Shift、Meta、F11 的按下/松开，以及鼠标捕获时的 W；
不记录文本输入或其他按键，不记录长按重复事件。窗口状态只在变化时输出。
该选项默认关闭；日志中的 `cursorDisabled` 是 GLFW 请求的模式，不能单独证明
合成器实际成功捕获了鼠标，也不能证明系统快捷键是否被拦截。

```bash
./gradlew clean build --console=plain
```

发行 JAR 是 fat JAR，包含 vendored 兼容层源码、LWJGL3 Java API 和三平台 natives
。工程不执行 ForgeGradle 反混淆：本 Mod 不包含 Minecraft
类，而旧版反混淆器无法安全读取 LWJGL3 的多版本字节码。构建时不生成二进制
facade 或 JarJar 中间产物，并在 `verifyJar` 阶段检查 LWJGL2 类没有泄漏。

## 设计说明

`Lwjgl3Coremod` 通过 `IFMLLoadingPlugin` 在 Forge 初始化时运行，先把包含
LWJGL3 的当前 fat JAR 前置到 LaunchWrapper 的实际查找顺序，然后解除
`org.lwjgl.*` 的父加载器委派，再检测平台并通过反射调用 `GlfwInitHint`，
最后注册 `Lwjgl3ClassTransformer`。启动校验还会检查 `GL11` 的 CodeSource，
若它仍来自启动器自带的 LWJGL2 JAR 则立即终止。转换器把旧 LWJGL2
API 的链接重写到 `org.lwjglx`；`GL11..GL45` 保持为 LWJGL3 原生绑定，因此
渲染命令不会经过 LWJGL2 类。Wayland + XWayland 模式使用 GLFW X11 backend，
由 XIM/Fcitx5 完成中文 commit，兼容层的 char callback 将 UTF-32 codepoint
放入 Minecraft 事件队列。

核心插件的 `SortingIndex` 为 `1001`，让 LWJGL 描述符重写在 Forge 的
反混淆转换（顺序 `1000`）之后运行。Forge 使用原始字段/方法描述符查找
SRG 名称；提前将 `Vector3f` 等类型改成 `org.lwjglx` 会让查找失败，导致
Glide 渲染掉落物等场景发生 `NoSuchFieldError`。

本项目参考了 [GTNewHorizons/lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify)、
[Zarzelcow/legacy-lwjgl3](https://github.com/Zarzelcow/legacy-lwjgl3) 和
[gudenau/MC-LWJGL3](https://github.com/gudenau/MC-LWJGL3)。兼容层代码依照
其上游许可证随 JAR 分发，详见 `THIRD_PARTY_NOTICES.md`。

## 已知边界

GLFW 3.3 在原生 Wayland backend 上没有实现 text-input-v3/XIM；因此要让
Fcitx5 在 1.8.9 的聊天框、铁砧和书本界面稳定提交中文，默认必须使用
XWayland/X11。`-Dlllllwjgl3.backend=wayland` 仍可用于纯 Wayland 渲染，但
不会承诺中文组合输入；这是 GLFW 原生能力限制，不是 Minecraft API 的限制。
若要去掉 XWayland，需要把窗口层替换为带 SDL text-input-v3 或自定义
Wayland text-input 协议的 native backend。

## 许可证

本项目以 [GPL-3.0-or-later](LICENSE) 发布。随 JAR 分发的第三方组件
（legacy-lwjgl3、MC-LWJGL3 兼容层和 LWJGL 2/3）沿用各自的
许可证，均与 GPL-3.0 兼容，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
与 `licenses/` 目录。
