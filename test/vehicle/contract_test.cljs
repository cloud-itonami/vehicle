(ns vehicle.contract-test
  "vehicle の面どうしの契約を固定する。

  この repo は薄い edge であって、business logic は AgentGateway MCP と
  pod 側 LangServer に居る。したがってこの repo の実体はコードではなく、
  **複数の面が同じ actor・同じ安全境界について同じことを言っている**という
  合意である:

    src/app.ts                          thin edge（/health・NSID guard・dispatcher 転送）
    src/xrpc-agentgateway-proxy.ts       退避された XRPC backend（未配線、旧 svelte/ から移動）
    cljs/src/vehicle/app.cljs           配備される frontend（static asset、jp-go-dds/reagent/re-frame）
    wrangler.jsonc                      配備（assets / routes / vars）
    kotodama.jsonld                     actor identity（DID / nanoid / capabilities）
    package.json                        version
    migration.edn                       抽出元の path

  どの面も他を import していないので、片方だけ動いた drift は throw しない
  —— identity 文書と worker が別の nanoid を名乗っても配備は成功し、
  fleet の逆引きが別人を指して初めて分かる。

  ## svelte/ は cljs/ に置き換わった（2026-09-07 実測）

  以前は `wrangler.main` が SvelteKit の build 出力を指し、`src/app.ts` は
  配備の実行経路に**入っていなかった**。Svelte → ClojureScript の frontend
  移行で `svelte/` を削除したため、その build 出力自体がもう存在しない。
  いま `wrangler.jsonc` に `main` は無く、frontend は
  `assets.directory: \"./cljs/public\"`（`cljs/src/vehicle/app.cljs` が
  ビルドする static asset）として配備される。`src/app.ts` は依然として
  `main` には指定されていない —— `env.ASSETS.fetch()` を呼ばないため、
  そこに置くと static asset を配る者がいなくなる（UNVERIFIED:
  `wrangler deploy`/`dev` はこの移行では実行していない）。一方
  `kotodama.build.edge` は引き続き `src/app.ts` を edge として名乗る。
  「実際に配備される静的 frontend」と「identity 文書が edge と名乗る面」は
  今もなお別物であり、どちらも現に存在する。ここは両方を pin して、片方が
  動いたら見えるようにする —— 「どちらが正か」はこのテストの決めることでは
  ない。

  ## 既知の drift: CLAUDE.md（2026-08-26 実測）

  `CLAUDE.md` は**別のアプリ**（vehicle ownership registry、nanoid
  `vh1cl3rk`、`wasm/` 配下の component）を記述している。この repo に
  `wasm/` は 1 ファイルも無く、他の 6 面はすべて Vehicle Manufacturing
  Graph / `v3h1cl01` で一致している。CLAUDE.md は identity 契約から
  外してあるが、**外したことを黙らせない**ために `known-stale-docs` に
  明示し、①その値が今も其処に在ること ②機械が読む面へ漏れていないこと
  の両方を検査する。CLAUDE.md を直した人は、このテストが赤くなることで
  『例外表から外して契約に入れる』ところまで手が届く。

  ## 抽出の床

  各抽出は見つからなければ throw する。**『抽出できなかった』が
  『合意している』と同じ顔をしてはならない** —— 正規表現は実装が
  変わると静かに空振りし、空振りは合格と同じ緑を返すからである。"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            ["fs" :as fs]))

;; ─── 抽出（見つからなければ throw） ─────────────────────────────────────

(defn- slurp-file [path] (fs/readFileSync path "utf8"))

(defn- extract-1
  "re の第 1 group を返す。見つからなければ throw —— 空振りを緑にしない。"
  [src re path what]
  (or (second (re-find re src))
      (throw (ex-info (str "extraction failed: " what " not found in " path)
                      {:path path :what what}))))

(defn- present!
  "literal が src に在ることを要求する。抽出の床（bool を返さない）。"
  [src literal path what]
  (when-not (str/includes? src literal)
    (throw (ex-info (str "extraction floor: " what " not found in " path)
                    {:path path :what what :literal literal})))
  literal)

(defn- get! 
  "map から k を取る。無ければ throw —— nil は『合意している』の顔をする。"
  [m k path what]
  (let [v (get m k ::missing)]
    (when (= v ::missing)
      (throw (ex-info (str "extraction floor: " what " missing in " path)
                      {:path path :what what :key k})))
    v))

(defn- non-empty! [coll path what]
  (when (empty? coll)
    (throw (ex-info (str "extraction floor: 0 " what " extracted from " path)
                    {:path path :what what})))
  coll)

(defn- read-json [path]
  (js->clj (js/JSON.parse (slurp-file path)) :keywordize-keys true))

(defn- read-jsonc
  "wrangler.jsonc は行頭コメントだけを持つ。JSON に落として読む。"
  [path]
  (->> (str/split-lines (slurp-file path))
       (remove #(str/starts-with? (str/triml %) "//"))
       (str/join "\n")
       js/JSON.parse
       (#(js->clj % :keywordize-keys true))))

(def app-ts       (delay (slurp-file "src/app.ts")))
(def xrpc-route   (delay (slurp-file "src/xrpc-agentgateway-proxy.ts")))
(def kotodama     (delay (read-json "kotodama.jsonld")))
(def wrangler     (delay (read-jsonc "wrangler.jsonc")))
(def pkg          (delay (read-json "package.json")))
(def migration    (delay (slurp-file "migration.edn")))

;; ─── thin edge から読む事実 ─────────────────────────────────────────────

(def edge
  (delay
   {:actor-did    (extract-1 @app-ts #"actor: \"(did:web:[^\"]+)\"" "src/app.ts" "actor DID")
    :nanoid       (extract-1 @app-ts #"env\.APP_NANOID \?\? \"([^\"]+)\"" "src/app.ts" "APP_NANOID fallback")
    :nsid-prefix  (extract-1 @app-ts #"const NSID_PREFIX = \"([^\"]+)\";" "src/app.ts" "NSID_PREFIX")
    :dispatcher   (extract-1 @app-ts #"env\.DISPATCHER_URL \?\? \"([^\"]+)\"" "src/app.ts" "DISPATCHER_URL fallback")}))

;; ─── identity ───────────────────────────────────────────────────────────

(deftest actor-identity-agrees-across-surfaces
  (testing "nanoid は 5 箇所に別々に書かれている。1 箇所だけ動くと fleet 逆引きがずれる"
    (let [n (:nanoid @edge)]
      (is (= n (get-in @wrangler [:vars :APP_NANOID]))
          "src/app.ts の APP_NANOID fallback と wrangler の APP_NANOID")
      (is (= n (:nanoid @kotodama))
          "src/app.ts の APP_NANOID fallback と kotodama.jsonld の nanoid")
      (is (= (str "kotodama-" n) (:name @wrangler))
          "wrangler の worker 名は kotodama-<nanoid>")
      (is (= (str n ".etzhayyim.com/*")
             (get-in @wrangler [:routes 0 :pattern]))
          "wrangler の route は <nanoid>.etzhayyim.com")))

  (testing "DID は thin edge と identity 文書が同じものを名乗る"
    (is (= (:actor-did @edge)
           (get! @kotodama (keyword "@id") "kotodama.jsonld" "@id"))
        "src/app.ts の actor と kotodama.jsonld の @id")))

(deftest capabilities-agree-between-deploy-vars-and-identity-doc
  (testing "wrangler の APP_CAPABILITIES は JSON 文字列、kotodama は配列。同じ列であること"
    (let [declared (-> (get-in @wrangler [:vars :APP_CAPABILITIES])
                       js/JSON.parse
                       js->clj
                       (non-empty! "wrangler.jsonc" "APP_CAPABILITIES entries"))
          identity (-> (get-in @kotodama [:profile :capabilities])
                       (non-empty! "kotodama.jsonld" "profile.capabilities"))]
      (is (= declared identity)
          "APP_CAPABILITIES と profile.capabilities は順序まで含めて同一"))))

(defn- normalize-dashes
  "kotodama は em dash、wrangler は ASCII hyphen を使う（2026-08-26 実測の
   既知の byte 差）。語が変わったことは捕まえたいので、dash だけ畳む。"
  [s]
  (-> s (str/replace "—" "-") (str/replace "–" "-")))

(deftest descriptor-fields-agree-across-surfaces
  (let [vars (:vars @wrangler)]
    (is (= (:APP_DISPLAY_NAME vars) (get-in @kotodama [:profile :displayName]))
        "APP_DISPLAY_NAME と profile.displayName")
    (is (= (:APP_PERFORMER_TYPE vars) (:performerType @kotodama))
        "APP_PERFORMER_TYPE と performerType")
    (is (= (:APP_UI_TYPE vars) (:uiType @kotodama))
        "APP_UI_TYPE と uiType")
    (is (= (:APP_TEMPLATE vars) (:template @kotodama))
        "APP_TEMPLATE と template")
    (testing "version は 3 面が言う"
      (is (= (:APP_VERSION vars) (:version @kotodama))
          "APP_VERSION と kotodama version")
      (is (= (:APP_VERSION vars) (:version @pkg))
          "APP_VERSION と package.json version"))
    (testing "抽出元の path は kotodama と migration.edn が言う"
      (is (= (:APP_SOURCE vars) (:source @kotodama))
          "APP_SOURCE と kotodama source")
      (is (str/includes? @migration (str ":path \"" (:APP_SOURCE vars) "\""))
          "APP_SOURCE と migration.edn の :source :path"))
    (testing "description は dash 表記だけが違う（語が変わったら赤くなる）"
      (is (= (normalize-dashes (:APP_DESCRIPTION vars))
             (normalize-dashes (get-in @kotodama [:profile :description])))
          "APP_DESCRIPTION と profile.description（dash 正規化後）"))))

;; ─── thin edge の安全境界 ───────────────────────────────────────────────

(deftest xrpc-guard-confines-the-thin-edge-to-the-vehicle-lexicon
  (testing "guard の prefix が動くと、他 actor の XRPC が dispatcher へ抜ける"
    (is (= "com.etzhayyim.apps.vehicle." (:nsid-prefix @edge))
        "NSID_PREFIX は vehicle lexicon"))

  (testing "prefix は転送分岐そのものを守っている（定数が在るだけでは足りない）"
    (is (re-find #"nsid\.startsWith\(NSID_PREFIX\)[^\n]*\{" @app-ts)
        "proxyToDispatcher の分岐が nsid.startsWith(NSID_PREFIX) で閉じている"))

  (testing "prefix に合わない経路は 404 で終わる"
    (is (re-find #"\"NotFound\"[^\n]*\}, 404\)" @app-ts)
        "既定の返答は 404 NotFound")))

(deftest thin-edge-omits-the-internal-trust-header-when-no-secret-is-bound
  (testing "secret が無いときに空文字を header に載せると、上流が信頼済みと誤読しうる"
    (is (re-find #"if \(trust\) headers\[\"x-internal-trust\"\]" @app-ts)
        "x-internal-trust は trust が truthy のときだけ載る")
    (is (re-find #"if \(!binding\) return \"\";" @app-ts)
        "binding が無ければ空文字（throw でも undefined でもない）")
    (is (re-find #"catch \{\s*return \"\";" @app-ts)
        "SecretBinding.get() が throw しても空文字に落ちる")))

(deftest thin-edge-rejects-malformed-json-instead-of-forwarding-it
  (testing "壊れた JSON を素通しすると、上流が空 body を正当な要求と読む"
    (is (re-find #"body\.__invalidJson\) return json\(\{ error: \"InvalidJson\" \}, 400\)" @app-ts)
        "__invalidJson は 400 InvalidJson")))

(deftest query-params-never-overwrite-the-posted-body
  (testing "query が body を上書きできると、POST 済みの値を URL から差し替えられる"
    (is (present! @app-ts "if (!(k in body)) body[k] = v;" "src/app.ts" "body-wins merge")
        "query は body に無い key のときだけ入る")))

(deftest dispatcher-base-url-is-pinned-and-slash-normalized
  (is (= "https://dispatcher.etzhayyim.com" (:dispatcher @edge))
      "DISPATCHER_URL の既定値")
  (is (re-find #"\.replace\(/\\/\+\$/, \"\"\)" @app-ts)
      "末尾スラッシュを畳んでから /xrpc/ を継ぐ"))

;; ─── 実際に配備される面 ─────────────────────────────────────────────────

(deftest deployed-entry-is-static-assets-not-the-sveltekit-build
  (testing "main は無い。frontend は cljs が焼く static asset として配備される
            —— identity 文書が edge と名乗る path とはやはり別物である
            （2026-09-07 実測、svelte → cljs 移行後）"
    (is (not (contains? @wrangler :main))
        "wrangler.jsonc に main は無い（旧 SvelteKit build 出力は削除済み）")
    (is (= "./cljs/public" (get-in @wrangler [:assets :directory]))
        "wrangler.assets.directory は cljs のビルド出力を指す")
    (is (= "cljs-reagent-re-frame-jp-go-dds" (get-in @wrangler [:vars :APP_FRAMEWORK]))
        "APP_FRAMEWORK は移行後の frontend framework を名乗る")
    (is (str/ends-with? (get-in @kotodama [:build :edge]) "/src/app.ts")
        "kotodama.build.edge は src/app.ts を名乗る（変わっていない）")))

(deftest xrpc-agentgateway-proxy-carries-the-sveltekit-backend-preserved-marker
  (testing "退避されたことが、ファイル自身から読める形で書いてある"
    (is (str/starts-with?
         @xrpc-route
         "// SVELTEKIT-BACKEND-PRESERVED: moved out of svelte/ during the cljs migration; not wired.")
        "src/xrpc-agentgateway-proxy.ts の先頭行は SVELTEKIT-BACKEND-PRESERVED マーカー")))

(deftest deployed-xrpc-route-does-not-cache-and-limits-preflight
  (testing "XRPC 応答が cache されると、actor 状態が別の閲覧者へ漏れる"
    (is (present! @xrpc-route "headers.set('cache-control', 'no-store');"
                  "src/xrpc-agentgateway-proxy.ts" "no-store on every response")
        "noStore が全応答に cache-control: no-store を付ける"))
  (testing "preflight は POST に閉じる"
    (is (present! @xrpc-route "'access-control-allow-methods': 'POST,OPTIONS'"
                  "src/xrpc-agentgateway-proxy.ts" "preflight methods")
        "許可 method は POST,OPTIONS のみ"))
  (testing "inbound の host header を上流へ持ち越さない"
    (is (present! @xrpc-route "headers.delete('host');"
                  "src/xrpc-agentgateway-proxy.ts" "host header stripped")
        "host は削ってから上流へ送る")))

;; ─── 既知の drift を封じ込める ─────────────────────────────────────────

(def stale-identifier
  "CLAUDE.md が名乗る、この repo のものではない nanoid。機械が読む面へ
   1 文字でも漏れたら fleet 逆引きが別人を指す。"
  "vh1cl3rk")

(def known-stale-docs
  "2026-08-26 実測。CLAUDE.md は別アプリ（ownership registry / nanoid
   vh1cl3rk / wasm 配下の component）を記述しており、この repo に wasm/ は
   1 ファイルも無い。identity 契約からは外すが、**外したことを黙らせない**。
   CLAUDE.md を直したらこのテストが赤くなる —— そのときは此処から外して
   actor-identity-agrees-across-surfaces に入れる。

   値は **1 箇所しか出てこない anchor** にする。bare な `vh1cl3rk` は
   CLAUDE.md に 4 回出るので、1 行だけ直した編集を『まだ古いまま』と
   読んでしまう —— 部分的に直った文書は、直っていない文書と同じ顔をする。"
  {"CLAUDE.md" "https://vh1cl3rk.etzhayyim.com/health"})

(def machine-read-surfaces
  ["src/app.ts" "wrangler.jsonc" "kotodama.jsonld" "package.json"
   "project.json" "migration.edn"
   "src/xrpc-agentgateway-proxy.ts"
   "cljs/src/vehicle/app.cljs"])

(deftest stale-doc-drift-has-not-spread-to-any-machine-read-surface
  (testing "間違った nanoid が配備される面に載ると、fleet 逆引きが別人を指す"
    (doseq [path machine-read-surfaces]
      (is (not (str/includes? (slurp-file path) stale-identifier))
          (str path " が古い識別子 " stale-identifier " を持っていない")))))

(deftest known-stale-docs-are-still-stale
  (testing "例外表が現実とずれたら、例外表の方を直させる"
    (doseq [[path anchor] known-stale-docs]
      (is (str/includes? (slurp-file path) anchor)
          (str path " はもう " anchor " を含まない —— 直ったなら "
               "known-stale-docs から外して identity 契約に入れること")))))
