# Security Policy

## Supported versions

最新版のみをセキュリティ更新の対象とします。配布ページに掲載したSHA-256と署名証明書のフィンガープリントを照合できるようにします。

## Reporting a vulnerability

公開Issueへ脆弱性の詳細、個人データ、署名鍵情報を投稿しないでください。GitHubリポジトリの **Security → Report a vulnerability**（Private vulnerability reporting）が利用できる場合は、そこから非公開で報告してください。利用できない場合は、再現手順を伏せたIssueで非公開連絡先の案内を依頼してください。

受領後は、影響範囲の確認、修正版の作成、署名付きAPKとハッシュの公開、必要に応じたアドバイザリ公開の順で対応します。

## Release key

Androidのリリース署名鍵はリポジトリ外に保存し、パスワードはWindows DPAPIで現在のWindowsアカウントに暗号化して保存します。鍵、平文パスワード、生成済み署名設定をGitへ追加しません。
