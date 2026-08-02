# Oboegaki

「やること」と「メモ」を、4方向スワイプで軽快に処理するオフライン優先アプリです。Android と iOS で Domain・データ・UI を共有する Kotlin Multiplatform / Compose Multiplatform 構成です。

- 新規インストール時は空の状態で始まり、操作ガイドを表示します。
- テーマでは配色、フォント、アイコン、形、余白、影、動きを編集できます。
- 追加ボタンの横位置と高さ、下部・上部ボタンの順番を設定から変更できます。
- 3画面は下部ナビゲーションまたは左右スワイプで切り替わり、スワイプ中は隣画面が指の動きへ追従します。
- 日付はカレンダー、時刻は専用の時刻選択UIから設定できます。
- 端末の戻る操作は、テーマ編集や設定など一つ前の画面へ戻ります。
- 操作ガイドは「すべて」→「設定」からいつでも再表示できます。

## 開発環境

- Android Studio（JBR 17以上）
- Android SDK 36 / Build Tools 36.0.0
- Xcode 16以上（iOSビルド時。macOSのみ）

## ビルド

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
./gradlew.bat :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug
```

APKは `androidApp/build/outputs/apk/debug/androidApp-debug.apk` に生成されます。

## iOS

iOS側はmacOSとXcodeが必要です。`iosApp/project.yml` はXcodeGen用のプロジェクト定義です。

```bash
brew install xcodegen
cd iosApp
xcodegen generate
open Oboegaki.xcodeproj
```

初回起動時にXcodeでDevelopment Teamを選択してください。ビルド時に共通のKotlinフレームワークが自動生成されます。

## 構成

- `shared`: 共通モデル、Domain規則、Room KMP、Compose UI
- `androidApp`: Androidエントリポイント、通知、ファイル連携
- `iosApp`: SwiftUIのiOSエントリポイントとXcodeGen定義

詳細な要件は同梱の `SPECIFICATION.md`、実装状況は `CODEX_TASKS.md` を参照してください。
