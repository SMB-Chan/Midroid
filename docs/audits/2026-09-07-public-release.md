# Midroid 一般公開前監査 — 2026-09-07

判定: **公開見送り（NO-GO）**。認証ヘッダーの転送境界にP1が1件、機能・省電力・更新経路にP2が4件残る。CI合格を確認したが、公開候補の署名付きrelease APKの実機検証と軽量性の比較測定は未確認。

## 対象とGitHubの状況

- 確認日時: 2026-09-07 13:24 JST。
- リポジトリ: [SMB-Chan/Midroid](https://github.com/SMB-Chan/Midroid)、visibilityはprivate。
- 監査対象: [chore/public-release-prep / c78670163729e730aba0456e5f264f2dd12c2c3e](https://github.com/SMB-Chan/Midroid/commit/c78670163729e730aba0456e5f264f2dd12c2c3e)。最新候補を `/tmp/midroid-public-audit-20260907` に取得。
- [PR #4](https://github.com/SMB-Chan/Midroid/pull/4) の前回監査修正は候補へ統合済み。
- [PR #3](https://github.com/SMB-Chan/Midroid/pull/3) はopen、baseは `feat/android-webview-mvp`。この候補はまだmainに入っていない。
- [PR #1](https://github.com/SMB-Chan/Midroid/pull/1) はdraft。main `4f946c7` のルートにはREADMEとLICENSEのみ。
- [PR #2](https://github.com/SMB-Chan/Midroid/pull/2) はdraftで競合あり。古い個別修正PRなので、候補との重複・差分整理が必要。
- [最新CI run 34082364354](https://github.com/SMB-Chan/Midroid/actions/runs/34082364354) はsuccess、headShaは監査対象と一致。ログで `lint testDebugUnitTest assembleDebug assembleRelease`、R8、unsigned release出力確認の成功を確認。
- Releasesは0件。取得した直近100 workflow runsにPublic APK Releaseの実行履歴はない。workflow APIは404であり、過去全期間の実行不存在まで証明するものではない。
- 必要な署名Secretsの名前4件は存在する。値は取得していない。存在確認は、候補APKの署名・更新検証の代わりにはならない。
- 保護設定/rulesets APIは現行privateプランで403、private vulnerability reporting APIは404。設定済みとは判定できない。

## 指摘

### F1 — P1: HTTPSリダイレクト先へCookie等が再送される

対象:

- [MainActivity.kt:610–612](https://github.com/SMB-Chan/Midroid/blob/c78670163729e730aba0456e5f264f2dd12c2c3e/app/src/main/java/dev/midroid/app/MainActivity.kt#L610-L612)
- [NativeAudioPlayerDialog.kt:208–212](https://github.com/SMB-Chan/Midroid/blob/c78670163729e730aba0456e5f264f2dd12c2c3e/app/src/main/java/dev/midroid/app/media/NativeAudioPlayerDialog.kt#L208-L212)

ダウンロードは、開始URL用CookieをDownloadManagerの固定リクエストヘッダーへコピーする。音声は、最初のURLを同一originと判定した後、CookieとRefererをDefaultHttpDataSourceの既定ヘッダーへコピーする。どちらも転送先ごとの再判定がない。

再現条件: HTTPSの同一originのファイルURLが別HTTPSホスト（CDN等）へ302等で転送され、開始URLにCookieが存在する。コピーされたCookieが転送先へ送られ得る。音声では元ページの完全なRefererも対象になる。Cookieに認証情報を持つ構成では認証情報漏えいになる。Misskeyの全構成でCookieがログイントークンとは断定しない。

根拠:

- [AOSP DownloadThread](https://raw.githubusercontent.com/aosp-mirror/platform_packages_providers_downloadprovider/main/src/com/android/providers/downloads/DownloadThread.java) はリダイレクトを手動で追跡し、各接続で同じ `mInfo.getHeaders()` を再追加する。
- [Media3 1.11.0 DefaultHttpDataSource](https://raw.githubusercontent.com/androidx/media/1.11.0/libraries/datasource/src/main/java/androidx/media3/datasource/DefaultHttpDataSource.java) の `setAllowCrossProtocolRedirects(false)` はHTTPS→HTTPSのホスト変更を禁止しない。通常経路はHttpURLConnectionに自動追跡させる。
- [Android組み込みOkHttpのHttpEngine](https://android.googlesource.com/platform/external/okhttp/+/refs/heads/main/okhttp/src/main/java/com/squareup/okhttp/internal/http/HttpEngine.java) の `followUpRequest()` はorigin変更時にAuthorizationを削除するが、手動設定したCookie/Refererを同様には削除しない。

前回修正の「外部URLを直接指定したときにCookieを取得しない」は有効。しかし、開始時だけのorigin検査ではこの経路を防げない。これは直接外部URLへのCookie取得を誤って指摘するものではない。

必要な修正: 認証付き転送は各hopを制御し、origin変更時にCookie/Refererを除去するか転送を拒否する。DownloadManagerへ認証ヘッダー付きURLを無条件で渡す設計を見直す。事前HEAD検査だけでは、その後のGETとの間で転送先が変わるため不十分。HTTPS別ホスト・別port・同一origin・複数段redirectの回帰試験を追加する。

確認水準: 候補コードと依存実装の静的追跡で確認。実機での通信キャプチャは未実施。

### F2 — P2: 音声プレイヤーが非表示後も準備・進捗更新を続け、遅延READYで再生を要求する

対象: [NativeAudioPlayerDialog.kt:236–246](https://github.com/SMB-Chan/Midroid/blob/c78670163729e730aba0456e5f264f2dd12c2c3e/app/src/main/java/dev/midroid/app/media/NativeAudioPlayerDialog.kt#L236-L246)、同ファイル70–79/288–294、MainActivity.kt:134。

再現手順: 遅い音声URLのネイティブ再生を開始し、準備完了前にHomeへ移動する。onStopは `pause()` を呼ぶだけで、初回READY時の自動再生意図を解除しない。その後のREADYで `startPlayback()` が無条件に呼ばれる。OSの音声フォーカス制約により実際に音が出るかは異なるが、アプリ側は非表示で再生を要求する。またprogressUpdaterは停止・非表示状態を見ず500 msごとに再登録され、pauseでは取り除かれない。これはCPUを強制的に起こすwakelockとは別だが、プロセスが実行できる間は不要なコールバックが残る。

必要な修正: 可視性と再生意図を状態として管理し、onStopで自動再生意図・進捗更新・必要に応じて準備処理を停止する。復帰時も意図しない自動再生をしない。遅延READY/BUFFERING/ERRORとonStopの順序をテストする。

確認水準: コードの状態遷移で確認。実機の音声出力・消費電力は未計測。

### F3 — P2: Ecoの自動再生抑制がSPAで後から追加されたメディアに効かない

対象: [WebViewPowerController.kt:121–124](https://github.com/SMB-Chan/Midroid/blob/c78670163729e730aba0456e5f264f2dd12c2c3e/app/src/main/java/dev/midroid/app/power/WebViewPowerController.kt#L121-L124)。

`mediaPlaybackRequiresUserGesture=false` の一方、Ecoは適用時点の `video[autoplay], audio[autoplay]` を一度走査するだけ。適用箇所はforeground/page-readyで、Misskeyの追加タイムライン取得やSPA表示更新のたびには実行されない。後から現れるautoplayメディアは抑制を受けない。

候補の実際の注入JavaScriptを抽出し、最小DOM fixtureで「既存要素は停止・autoplay解除」「追加要素はautoplayが残る」を再現した。これはブラウザの実際の再生や省電力量を測定するテストではない。

必要な修正: 対象をメディアに絞った追加要素監視または再生イベント制御でSPA更新を扱う。ユーザーが明示的に開始した再生を妨げないこと、監視自体が軽量であることを検証する。

### F4 — P2: 保存済みの追加アカウントが非対応WebViewで起動すると復帰UIがない

対象: [MainActivity.kt:364–369](https://github.com/SMB-Chan/Midroid/blob/c78670163729e730aba0456e5f264f2dd12c2c3e/app/src/main/java/dev/midroid/app/MainActivity.kt#L364-L369)。

再現条件: MULTI_PROFILE対応providerで追加アカウントを選択した状態を保存し、非対応providerへの切替・ダウングレード後にアプリを起動する。onCreateからshowBrowserに入り、Toastだけでreturnする。まだsetContentViewされていないため、アカウント切替ボタンも設定画面もなく、既存defaultアカウントを選べない。ドキュメントの「defaultアカウントを引き続き使用できる」と一致しない。

必要な修正: 非対応時には復旧画面やdefaultアカウントの選択肢を表示し、named profileをdefaultストレージで開くことなく復帰できるようにする。WebView生成例外の経路にも復旧UIを用意する。

確認水準: 起動経路の静的追跡。provider切替を伴う実機テストは未実施。

### F5 — P2: 公開APKのversionCodeがrun番号の100境界で逆転する

対象: [.github/workflows/release.yml:40–42](https://github.com/SMB-Chan/Midroid/blob/c78670163729e730aba0456e5f264f2dd12c2c3e/.github/workflows/release.yml#L40-L42)。

日付に `GITHUB_RUN_NUMBER % 100` を付加する方式なので、累積run番号が99→100などの境界を同じUTC日に跨ぐと `2026090799 → 2026090700` と小さくなる。1日に100回リリースしなくても、累積番号がその境界なら発生する。新APKを通常の上書き更新としてインストールできなくなる。

必要な修正: 既存配布済みversionCodeを踏まえた単調増加方式を採用し、直近配布物より大きいことをrelease gateで検証する。run再実行、境界、古いtagの後日ビルドも扱う。docs/RELEASING.md:50の「run番号を単調増加versionCodeに使用する」という説明も実装と合わせる。

確認水準: 候補の式を照合し、境界の逆転をローカル再現。

## 軽量性・配布物の確認

- CIの最新debug APKを取得し、添付SHA-256と一致した。
- サイズ: 5,572,757 bytes、約5.31 MiB。DEXは9ファイル、APK同梱のnative libraryは0。WebView本体はOS側なので、このAPKサイズは総ストレージ使用量・RAM使用量を示さない。
- debug APKは `0.1.0-ci.254` / versionCode 254、minSdk 26 / targetSdk 36、debuggable=true、allowBackup=false、cleartext=false。
- CIの署名レポートはAndroid Debug証明書を示す。これは一般配布用の署名検証ではない。release APKの最終サイズ・署名・manifestは本監査では検証できていない。
- 統合後debug manifestにはINTERNETに加えてACCESS_NETWORK_STATE、WAKE_LOCK、アプリ固有のDYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSIONがある。PRIVACY.md:25の説明を統合後release manifestと照合して更新する。権限の存在だけでwakelock保持や悪意ある処理を意味しない。
- ソース上、WebViewはアカウント切替時に破棄し一つだけ維持する設計。releaseはR8/minify/resource shrinkが有効。これらは軽量性に寄与し得る設計である。
- HTTP cacheは空き容量に応じ、profileごとに少なくとも64/128/256 MiBのquotaへ増加する。quotaは事前確保量ではなく、service worker等を含む総保存量の上限でもない。複数profileの合計、削除・容量回収の手段は今後検証が必要。
- WebSocket、service worker、WebView rendererを含むバックグラウンド処理が完全に止まることは `pauseTimers()` だけからは保証できない。
- GitHubのコード・docsとローカル既存実機調査記録には、候補でのChrome/PWA対照による反復A/B/A測定を確認できなかった。既存記録は古いAPKの画像遅延調査で、公開候補の省電力効果を裏付けない。

「軽量化を目指す実験的クライアント」という現状の説明は妥当だが、「Chrome/PWAより低消費電力・低メモリ」と実測済みの事実として宣伝できる状態ではない。

## セキュリティと検証済み事項

- gitleaks公式v8.30.1を取得し、配布チェックサム照合後、取得した全5ブランチに対して `gitleaks git --log-opts=--all --redact` を実行。158コミット、約319 KBの差分を検査し検出0件。これは未追跡ファイル・外部添付物・未知の形式を含む秘密情報不存在の保証ではない。
- 直接依存3件（Activity 1.13.0 / WebKit 1.17.0 / Media3 ExoPlayer 1.11.0）をGitHub Advisory DatabaseのMaven/affectsで照会し、該当報告0件。推移依存の完全なSBOM照合、WebView provider本体のCVE検査は未実施。
- HTTPS instance検証、originのport比較、TLSエラー非迂回、fileアクセス禁止、third-party cookies無効、backup無効、debug限定のWebView inspectionを確認。
- 前回修正のorigin限定WebMessageListener、isMainFrame確認、直接外部音声URLのCookie隔離は候補に存在する。
- 私有scheme fallbackは対応WebViewでもnative側で受理可能。isForMainFrameは遷移先フレームの情報で、発信元origin認証の代わりにならないため、fallbackの廃止または対応provider時の無効化を追加検討する。本監査ではクロスorigin iframeからの実機再現まで行っておらず、確定指摘5件には含めていない。
- 診断コードのURL path/query/fragment除外を確認。
- 候補の `git diff --check` とshell toolingの `bash -n` は成功。
- JVMテストはソース上44個の@Test、10ファイル。CIのtestDebugUnitTest成功を確認したが、テストXMLは成果物に含まれず、実行件数をXMLから照合してはいない。
- ローカルにGradle/Android SDK 36がなく、ADB接続端末も0台。フルビルドは最新GitHub CIのログで確認し、ローカルで再実行したとは扱っていない。

## 公開前に閉じる作業

1. F1を修正し、ダウンロード・音声両経路で認証情報がredirect先へ流れないことを試験する。
2. F2–F5を修正し、ライフサイクル、SPAメディア、provider非対応起動、versionCode境界の回帰試験を実施する。
3. 候補をmainへ統合し、統合後SHAのCIを確認する。古いPR #2の扱いも整理する。
4. 署名付き非debug release APKを少なくとも1台の物理端末で確認する。起動、ログイン維持、投稿/添付、同一instanceの別アカウント、別instance、download、Back、音声、renderer復旧、回転/IME/insetsを含める。最低API付近とAPI 35/36の差分も確認する。
5. 永続鍵で署名した前後2版を使い、上書き更新・ログイン維持・versionCode増加・証明書一致を確認する。初回なので既存公開releaseからの更新は検証できないが、テスト用の前後2版で経路を検証できる。
6. README.md:111の「通常CIもupdate-compatible artifactを作る」は現行workflowと矛盾するため修正する。PRIVACYの統合後権限、RELEASINGのversionCode説明も合わせる。
7. private vulnerability reportingの利用可否と連絡先を具体化する。SECURITY.mdの「private GitHub channel」は宛先がなく、報告者が使える代替連絡方法として不十分。公開時のbranch protection/rulesetsを確認する。
8. 公開前のprivateリポジトリでrelease workflowを試す場合、checkoutの `persist-credentials:false` 後の無認証 `git fetch origin main` は失敗するため対処する。公開後の匿名fetchでは同じ問題は起きない。既にfetch済みのrefを検査する等、鍵を扱うジョブに認証情報を余計に残さない方法がある。
9. 省電力を実績として掲げる前に、同じ端末・instance・操作・温度条件でChrome/PWA、Balanced、Ecoを各3回以上比較する。foreground idle、スクロール、background、メディアでCPU/RAM/通信/フレーム/電力量を記録する。

## 監査成果物と変更範囲

この監査で作成したのは本報告書と再現スクリプトのみ。アプリの実装変更、GitHubへのコメント投稿、PR更新、マージ、tag作成、公開設定変更は行っていない。元の作業ブランチと未追跡の `tools/bootstrap_update_signing.sh` は維持している。

再現スクリプト: `2026-09-07-regressions.mjs`。候補checkoutの絶対パスを引数に指定して実行する。脆弱な挙動を確認する監査用fixtureであり、修正後に成功すべき通常の回帰テストではない。

```sh
node docs/audits/2026-09-07-regressions.mjs /tmp/midroid-public-audit-20260907
```
