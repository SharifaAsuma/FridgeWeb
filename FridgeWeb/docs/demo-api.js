/*
 * ===================================================================
 * GitHub Pages で「見た目だけ」を公開するための、デモ専用の仕組み。
 *
 * 本物のアプリ(script.js)は /api/items のようなURLに fetch() で通信するが、
 * GitHub Pages は静的ファイルしか置けず、Javaのサーバーもデータベースも動かせない。
 *
 * そこでこのファイルは、ブラウザ標準の fetch 関数を「差し替えて」、
 * /api/... へのアクセスがあったときに、サーバーの代わりにこのファイルの中の
 * ダミーデータで応答するようにしている(= フェイクのサーバー)。
 *
 * script.js 自体は、本物のサーバーと話しているつもりで今まで通り動くので、
 * アプリ側のコードは一切変更していない。
 *
 * 読み込み順が重要: index.html で、このファイルを script.js より先に読み込むこと。
 * ===================================================================
 */
(function () {
  const WARNING_DAYS = 3;

  // ---- 日付の補助 ----
  const todayStr = () => new Date().toISOString().slice(0, 10);
  const addDays = (n) => {
    const d = new Date();
    d.setDate(d.getDate() + n);
    return d.toISOString().slice(0, 10);
  };

  // ---- デモ用の見本データ(ページを開くたびにリセットされる。保存はされない) ----
  let seq = 1;
  const nextId = () => seq++;
  let items = [
    { id: nextId(), name: "豚こま肉", category: "肉類", expiry: addDays(-2), purchase: todayStr(), price: 398, store: "ライフ", note: "冷凍しておく", disposalDate: null },
    { id: nextId(), name: "キャベツ", category: "野菜類", expiry: addDays(2), purchase: todayStr(), price: 158, store: "イオン", note: "", disposalDate: null },
    { id: nextId(), name: "卵", category: "卵🥚", expiry: addDays(10), purchase: todayStr(), price: 248, store: "まいばすけっと", note: "", disposalDate: null },
    { id: nextId(), name: "牛乳", category: "乳製品", expiry: addDays(20), purchase: todayStr(), price: 218, store: "ライフ", note: "", disposalDate: null },
    { id: nextId(), name: "醤油", category: "調味料", expiry: addDays(90), purchase: "", price: 0, store: "", note: "", disposalDate: null },
    { id: nextId(), name: "鮭の切り身", category: "魚類", expiry: addDays(-10), purchase: addDays(-20), price: 328, store: "イオン", note: "使い切った", disposalDate: addDays(-1) },
    { id: nextId(), name: "あさり(冷凍)", category: "魚介類", expiry: addDays(30), purchase: todayStr(), price: 298, store: "業務スーパー", note: "", disposalDate: null },
    { id: nextId(), name: "冷凍餃子", category: "冷凍食品", expiry: addDays(45), purchase: todayStr(), price: 398, store: "コストコ", note: "", disposalDate: null },
    { id: nextId(), name: "食器用洗剤", category: "日用品", expiry: null, purchase: addDays(-5), price: 258, store: "西友", note: "", disposalDate: null },
  ];

  /** 今日からの残り日数(賞味期限が無ければ null) */
  function daysUntil(expiry) {
    if (!expiry) return null;
    return Math.round((new Date(expiry) - new Date(todayStr())) / 86400000);
  }

  /** 本物のAPIと同じ形のJSONに変換する */
  function toApiShape(it) {
    return {
      id: it.id, name: it.name, category: it.category, expiry: it.expiry || null,
      purchase: it.purchase || null, price: it.price, store: it.store, note: it.note,
      disposed: !!it.disposalDate, daysUntilExpiry: daysUntil(it.expiry),
    };
  }

  /** fetch() の戻り値と同じ形(Response)でJSONを返す */
  function jsonResponse(data, status) {
    return Promise.resolve(new Response(JSON.stringify(data), {
      status: status || 200, headers: { "Content-Type": "application/json" },
    }));
  }

  /** 一覧を、本物のサーバーと同じ並び順(賞味期限が近い順、未設定は最後)に揃える */
  function sortedList(list) {
    return [...list].sort((a, b) => {
      if (a.expiry === null && b.expiry === null) return a.id - b.id;
      if (a.expiry === null) return 1;
      if (b.expiry === null) return -1;
      return a.expiry < b.expiry ? -1 : a.expiry > b.expiry ? 1 : a.id - b.id;
    });
  }

  const realFetch = window.fetch.bind(window);

  // ここから、fetch() 本体の差し替え。script.js が呼ぶURLのパターンごとに応答を作る
  window.fetch = function (url, options) {
    options = options || {};
    const path = new URL(url, location.href).pathname; // ドメインやフォルダの深さに関わらずパス部分だけで判定する
    const method = (options.method || "GET").toUpperCase();
    const idMatch = path.match(/\/api\/items\/(\d+)(?:\/(dispose|restore))?$/);

    if (path.endsWith("/api/meta") && method === "GET") {
      return jsonResponse({
        categories: [...new Set(items.map(i => i.category))],
        stores: [...new Set(items.map(i => i.store).filter(Boolean))],
        warningDays: WARNING_DAYS,
      });
    }

    if (path.endsWith("/api/items") && method === "GET") {
      const q = new URL(url, location.href).searchParams;
      const category = q.get("category");
      const includeDisposed = q.get("includeDisposed") === "true";
      const list = items.filter(it =>
          (!category || it.category === category) && (includeDisposed || !it.disposalDate));
      return jsonResponse(sortedList(list).map(toApiShape));
    }

    if (path.endsWith("/api/items") && method === "POST") {
      const body = JSON.parse(options.body);
      items.push({ ...body, id: nextId(), disposalDate: null });
      return jsonResponse({ ok: true });
    }

    if (idMatch && !idMatch[2] && method === "PUT") {
      const id = Number(idMatch[1]);
      const body = JSON.parse(options.body);
      const target = items.find(i => i.id === id);
      if (!target) return jsonResponse({ error: "見つかりません" }, 404);
      Object.assign(target, body);
      return jsonResponse({ ok: true });
    }

    if (idMatch && !idMatch[2] && method === "DELETE") {
      const id = Number(idMatch[1]);
      items = items.filter(i => i.id !== id);
      return jsonResponse({ ok: true });
    }

    if (idMatch && idMatch[2] === "dispose" && method === "POST") {
      const target = items.find(i => i.id === Number(idMatch[1]));
      if (target) target.disposalDate = todayStr();
      return jsonResponse({ ok: true });
    }

    if (idMatch && idMatch[2] === "restore" && method === "POST") {
      const target = items.find(i => i.id === Number(idMatch[1]));
      if (target) target.disposalDate = null;
      return jsonResponse({ ok: true });
    }

    // 上のどれにも当てはまらない場合は、念のため本物の fetch に流す(通常は来ない想定)
    return realFetch(url, options);
  };
})();
