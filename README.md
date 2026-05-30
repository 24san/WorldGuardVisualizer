# WG Region Visualizer

Paper + WorldGuard 7.x のリージョン範囲を、導入済み Fabric クライアントだけに枠線で表示するための2構成プロジェクトです。

- `server-plugin/`: Paper サーバープラグイン `WGRegionVisualizerBridge`
- `fabric-client/`: Fabric クライアントMod `WG Region Visualizer`

パーティクルは使わず、クライアント側のワールド描画で線を描画します。サーバー側はコマンド実行時に WorldGuard のリージョン情報を JSON 化し、Plugin Messaging Channel `san24:wgviz` でコマンド実行者のクライアントへ送ります。

## 対象環境

- Minecraft: `1.21.11`
- Java: `21`
- Server: Paper `1.21.11`
- WorldGuard: `7.x`
- WorldEdit: `7.x`
- Client: Fabric `1.21.11`
- Fabric API: `1.21.11` 対応版

## ビルド方法

Fabric Loom `1.16.x` は Gradle `9.4.x` 向けに公開されています。IntelliJ IDEA でビルドする場合は、Gradle JVM を Java 21 にし、Gradle 本体は `9.4.1` 以上を使ってください。

### まとめてビルド

```powershell
gradle build
```

成果物:

```text
build/libs/WGRegionVisualizerBridge.jar
build/libs/wg-region-visualizer.jar
```

### サーバープラグインのみ

```powershell
gradle :server-plugin:shadowJar
```

成果物:

```text
server-plugin/build/libs/WGRegionVisualizerBridge-1.0.0.jar
```

Maven でビルドしたい場合も `server-plugin/pom.xml` を残してあります。

```powershell
cd server-plugin
mvn package
```

### Fabric クライアントModのみ

```powershell
gradle :fabric-client:build
```

成果物:

```text
fabric-client/build/libs/wg-region-visualizer-1.0.0.jar
```

2026-05-30 時点では、Fabric API の 1.21.11 向け最新版として `0.141.4+1.21.11` を指定しています。Loader は Modrinth App の 1.21.11 環境に合わせて `0.18.4` 以上を要求します。環境によって Fabric API / Loader の最新版が変わる場合があります。その場合は `fabric-client/gradle.properties` の値を、Fabric 公式の 1.21.11 対応版に更新してください。クライアントModのコードは Fabric 公式ドキュメントのクラス名に合わせるため、Yarn ではなく `loom.officialMojangMappings()` を使っています。

### IntelliJ IDEA の Gradle 実行構成

実行構成の `Gradle プロジェクト` には、リポジトリルートを指定します。

```text
C:\Users\96962\IdeaProjects\WorldGuardVisualizer
```

`タスクと引数` は次のいずれかです。

```text
build
```

```text
:server-plugin:shadowJar
```

```text
:fabric-client:build
```

`No matching variant ... org.gradle.plugin.api-version ... 9.2.1` が出る場合は、IntelliJ の `Settings > Build, Execution, Deployment > Build Tools > Gradle` で Gradle を `9.4.1` 以上に変更してください。

`Could not find remapped.net.fabricmc...` や `minecraft-merged...` が出る場合は、Gradle のリポジトリ設定が Loom のローカル remap リポジトリを見られていません。このプロジェクトでは `settings.gradle` を `RepositoriesMode.PREFER_PROJECT` にして、Loom が追加するリポジトリを使えるようにしています。

## 導入方法

### サーバー側

1. `WGRegionVisualizerBridge.jar` を Paper サーバーの `plugins` に入れます。
2. `WorldGuard` と `WorldEdit` も `plugins` に入れます。
3. サーバーを起動します。

WorldGuard または WorldEdit が見つからない場合、プラグインは分かりやすいログを出して無効化します。

### クライアント側

1. `wg-region-visualizer.jar` を Fabric クライアントの `mods` に入れます。
2. Fabric API も `mods` に入れます。
3. 対象サーバーへ接続します。

サーバー側から送られた WorldGuard リージョン情報だけを表示します。Mod単体では WorldGuard リージョンは取得できません。

## サーバーコマンド

権限はすべてデフォルト `op` です。

- `wgviz.use`: 通常コマンド
- `wgviz.reload`: 設定再読み込み

```text
/wgviz show <regionId>
```

現在いるワールドの指定 WorldGuard リージョンを、実行者のクライアントModへ送信します。

```text
/wgviz all
```

現在いるワールドの全リージョンを送信します。`__global__` は除外します。送信数は `config.yml` の `max-regions-all` で制限されます。

```text
/wgviz hide
```

クライアントModへ `clear` を送り、表示を消します。

```text
/wgviz list
```

現在いるワールドのリージョンID一覧を表示します。

```text
/wgviz reload
```

`config.yml` を再読み込みします。

Tab補完に対応しています。

## クライアントコマンド

```text
/wgvizclient clear
```

クライアント側に保存されている表示中リージョンをすべて削除します。

```text
/wgvizclient list
```

現在受信して表示中のリージョン一覧を表示します。

## 表示仕様

- Cuboid は12辺の枠線を描画します。
- Polygon は上下の外周線と各頂点の縦線を描画します。
- その他の `ProtectedRegion` は bounding box の cuboid として送信します。
- 線の色はデフォルトでシアンです。
- 線の太さ、色、半透明面表示のON/OFFは `fabric-client/src/main/java/org/san24/wgregionvisualizer/client/VisualizerConfig.java` の定数で変更できます。
- リージョンID表示は TODO コメントとして残しています。

## 設定

サーバー側 `server-plugin/src/main/resources/config.yml`:

```yaml
max-regions-all: 200
max-payload-bytes: 30000
```

`max-payload-bytes` は Plugin Message が大きくなりすぎることを防ぐための上限です。巨大な polygon リージョンが上限を超えた場合、そのリージョンはスキップされます。

## 注意点

- `/wgviz show <regionId>` で表示します。
- `/wgviz hide` で非表示にします。
- クライアントModが入っていないプレイヤーに送っても、サーバー側でエラーにならないようにしています。ただし表示はされません。
- WorldGuard のリージョン情報はサーバー側から送られます。Mod単体では WorldGuard リージョンは取得できません。
- Fabric 側のワールド描画は Fabric 公式ドキュメントの `Rendering in the World` 26.1.2 の `LevelRenderEvents` / `RenderPipeline` 構成に合わせています。
