# レシート消耗品リマインダー

レシートの写真から購入履歴を蓄積し、消耗品がなくなる時期を予測して、登録した店舗の近くで通知する Android アプリです。

## 技術スタック
- **言語 / UI**: Kotlin 2.4 / Jetpack Compose (Material 3)
- **ビルドツール**: Gradle 9.5 / Android Gradle Plugin 9.3.0 / JDK 21
- **最小 / ターゲット SDK**: minSdk 26 / targetSdk 37 / compileSdk 37
- **データベース**: Room 2.8.5
- **非同期 / タスク**: Kotlin Coroutines 1.11.0 / WorkManager 2.12.0
- **OCR / スキャン**: ML Kit Text Recognition v2 (日本語) / ML Kit Document Scanner
- **位置情報**: Google Play Services Location (Geofencing API)

## 必要環境
- **Android Studio**: Android Studio 2026.1 (または互換の最新版、JDK 21 同梱)
- **Android SDK**: Android 37.0 (API 37)
- **エミュレータ**: Google Play Services 搭載のシステムイメージ (API 37 推奨)

## ビルドとテスト

### 単体テストの実行
```bash
./gradlew testDebugUnitTest
```
Windows (PowerShell):
```powershell
.\gradlew.bat testDebugUnitTest
```

### デバッグ APK のビルド
```bash
./gradlew assembleDebug
```
Windows (PowerShell):
```powershell
.\gradlew.bat assembleDebug
```
生成物: `app/build/outputs/apk/debug/app-debug.apk`

## プロジェクトのルール
- 開発の仕様は `REQUIREMENTS.md`、作業範囲は `TASKS.md`、設計は `PLAN.md` を正とします。
- 1 タスク 1 ブランチ 1 PR を厳守し、担当外のファイルは変更しません。
- サーバーを持たず、購入データ・位置情報・レシート画像は外部に送信しません。
- 自動バックアップは無効化されています。
