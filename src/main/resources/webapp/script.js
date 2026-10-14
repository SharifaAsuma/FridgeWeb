// =====================================================================
// 冷蔵庫の食材管理・画面の動き(JavaScript)。
// サーバー(Java)とは /api/... のJSON通信でやり取りする。
// このファイルの中の関数は、上から
//   1) 起動時の初期化
//   2) 一覧の取得・絞り込み・並び替え・描画
//   3) ジャンルの色分け(バッジ)
//   4) 追加・編集フォームの表示と保存
//   5) 使い切った/削除の操作
//   6) 細かい補助関数(日付・値段の表示、トースト、アイコンSVGなど)
// の順に並んでいる。
// =====================================================================

// ---- 今の絞り込み・並び替え条件を覚えておく変数 ----
let currentCategory = "";            // 選択中のジャンル("" = すべて)
let currentIncludeDisposed = false;  // 使い切った食材も表示するか
let currentSearch = "";              // 検索欄に入力されている文字列
let currentSort = { key: "expiry", dir: "asc" }; // 並び替えの列と向き
let lastItems = [];                  // 直近にサーバーから取得した一覧(検索・並び替えのたびに取り直さないため)
let WARNING_DAYS = 3;                // 賞味期限の警告日数(/api/meta から取得して上書きする)

// ---- HTML要素をまとめて取得しておく ----
const el = {
  categoryFilter: document.getElementById("categoryFilter"),
  includeDisposed: document.getElementById("includeDisposed"),
  searchBox: document.getElementById("searchBox"),
  openAddButton: document.getElementById("openAddButton"),
  emptyAddButton: document.getElementById("emptyAddButton"),
  statTotal: document.getElementById("statTotal"),
  statSoon: document.getElementById("statSoon"),
  statExpired: document.getElementById("statExpired"),
  itemsBody: document.getElementById("itemsBody"),
  emptyMessage: document.getElementById("emptyMessage"),
  toast: document.getElementById("toast"),
  sortableHeaders: document.querySelectorAll("th[data-sort]"),

  formOverlay: document.getElementById("formOverlay"),
  itemForm: document.getElementById("itemForm"),
  formTitle: document.getElementById("formTitle"),
  formErrors: document.getElementById("formErrors"),
  closeXButton: document.getElementById("closeXButton"),
  cancelButton: document.getElementById("cancelButton"),
  storeOptions: document.getElementById("storeOptions"),

  itemId: document.getElementById("itemId"),
  fieldName: document.getElementById("fieldName"),
  fieldCategory: document.getElementById("fieldCategory"),
  fieldExpiry: document.getElementById("fieldExpiry"),
  fieldPurchase: document.getElementById("fieldPurchase"),
  fieldPrice: document.getElementById("fieldPrice"),
  fieldStore: document.getElementById("fieldStore"),
  fieldNote: document.getElementById("fieldNote"),
};

// =====================================================================
// 1) 起動時の初期化
// =====================================================================

async function init() {
  el.categoryFilter.addEventListener("change", () => {
    currentCategory = el.categoryFilter.value;
    reloadList();
  });
  el.includeDisposed.addEventListener("change", () => {
    currentIncludeDisposed = el.includeDisposed.checked;
    reloadList();
  });
  // 検索は、サーバーに聞き直さず手元のデータを絞り込むだけにして、入力のたびすぐ反映されるようにする
  el.searchBox.addEventListener("input", () => {
    currentSearch = el.searchBox.value.trim().toLowerCase();
    renderFromState();
  });
  // 列見出しをクリックしたら、その列で並び替える(もう一度押すと昇順・降順が入れ替わる)
  el.sortableHeaders.forEach(th => {
    th.addEventListener("click", () => onSortClick(th));
    th.addEventListener("keydown", e => {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onSortClick(th); }
    });
  });

  el.openAddButton.addEventListener("click", () => openForm(null));
  el.emptyAddButton.addEventListener("click", () => openForm(null));
  el.closeXButton.addEventListener("click", closeForm);
  el.cancelButton.addEventListener("click", closeForm);
  el.itemForm.addEventListener("submit", onSubmitForm);

  // モーダルの背景(overlayそのもの)をクリックしたら閉じる。中のカードをクリックしたときは閉じない
  el.formOverlay.addEventListener("click", e => {
    if (e.target === el.formOverlay) closeForm();
  });
  // Escapeキーでモーダルを閉じる
  document.addEventListener("keydown", e => {
    if (e.key === "Escape" && !el.formOverlay.hidden) closeForm();
  });

  await loadMeta();   // ジャンル一覧・購入店舗の候補を先に読み込む
  await reloadList();
}

/** GET /api/meta : ジャンルの選択肢や購入店舗の候補・警告日数を、画面の各所に反映する */
async function loadMeta() {
  const meta = await fetchJson("/api/meta");
  WARNING_DAYS = meta.warningDays;

  for (const cat of meta.categories) {
    el.categoryFilter.appendChild(new Option(cat, cat));
    el.fieldCategory.appendChild(new Option(cat, cat));
  }
  for (const store of meta.stores) {
    el.storeOptions.appendChild(new Option(store));
  }
}

// =====================================================================
// 2) 一覧の取得・絞り込み・並び替え・描画
// =====================================================================

/** 今の絞り込み条件(ジャンル・使い切み表示)でサーバーから取り直す */
async function reloadList() {
  const params = new URLSearchParams();
  if (currentCategory) params.set("category", currentCategory);
  if (currentIncludeDisposed) params.set("includeDisposed", "true");

  lastItems = await fetchJson("/api/items?" + params.toString());
  renderFromState();
}

/** 手元にある lastItems に対して、検索 → 並び替え → 描画 を行う(サーバーには聞き直さない) */
function renderFromState() {
  const filtered = currentSearch ? lastItems.filter(matchesSearch) : lastItems;
  const sorted = sortItems(filtered, currentSort);
  renderSummary(filtered);
  renderTable(sorted);
}

/** 食材名・購入店舗・備考のいずれかに検索文字列が含まれているか */
function matchesSearch(item) {
  const text = `${item.name} ${item.store} ${item.note}`.toLowerCase();
  return text.includes(currentSearch);
}

/** 列見出しクリック時: 同じ列なら昇順・降順を反転、違う列なら新しく昇順から始める */
function onSortClick(th) {
  const key = th.dataset.sort;
  if (currentSort.key === key) {
    currentSort.dir = currentSort.dir === "asc" ? "desc" : "asc";
  } else {
    currentSort = { key, dir: "asc" };
  }
  // 見出しの ▲▼ 表示(aria-sort)を更新する。押した列以外は消す
  el.sortableHeaders.forEach(h => {
    h.setAttribute("aria-sort", h.dataset.sort === currentSort.key
        ? (currentSort.dir === "asc" ? "ascending" : "descending")
        : "none");
  });
  renderFromState();
}

/** items を sort.key ・ sort.dir の内容で並び替えた新しい配列を返す(元の配列は変えない) */
function sortItems(items, sort) {
  const factor = sort.dir === "asc" ? 1 : -1;
  const copy = [...items];
  copy.sort((a, b) => {
    let cmp;
    switch (sort.key) {
      case "name":
      case "category":
      case "store":
        cmp = a[sort.key].localeCompare(b[sort.key], "ja");
        break;
      case "price":
        cmp = a.price - b.price;
        break;
      case "expiry":
      case "purchase": {
        // 日付が未設定(null)のものは、並び順に関わらず常に最後に置く
        const av = a[sort.key], bv = b[sort.key];
        if (av === null && bv === null) cmp = 0;
        else if (av === null) return 1;
        else if (bv === null) return -1;
        else cmp = av < bv ? -1 : av > bv ? 1 : 0;
        break;
      }
      default:
        cmp = 0;
    }
    if (cmp === 0) cmp = a.id - b.id; // 同じ値なら、登録順(ID順)で安定させる
    return cmp * factor;
  });
  return copy;
}

/** 「全○件」「まもなく期限○件」「期限切れ○件」の数字を書き換える(使い切み済みは数えない) */
function renderSummary(items) {
  const active = items.filter(it => !it.disposed);
  const expired = active.filter(it => it.daysUntilExpiry !== null && it.daysUntilExpiry < 0).length;
  const soon = active.filter(it => it.daysUntilExpiry !== null && it.daysUntilExpiry >= 0
      && it.daysUntilExpiry <= WARNING_DAYS).length;

  el.statTotal.textContent = items.length;
  el.statSoon.textContent = soon;
  el.statExpired.textContent = expired;
}

/** 食材の配列から一覧表の行(<tr>)を作って表に反映する */
function renderTable(items) {
  el.itemsBody.innerHTML = "";
  el.emptyMessage.hidden = items.length > 0;

  for (const item of items) {
    const tr = document.createElement("tr");
    tr.className = rowClass(item);

    tr.appendChild(cell(item.name, "食材名", "name"));
    tr.appendChild(categoryCell(item.category));
    tr.appendChild(cell(item.expiry ?? "-", "賞味期限"));
    tr.appendChild(cell(formatRemaining(item.daysUntilExpiry), "残り"));
    tr.appendChild(cell(item.purchase ?? "-", "購入日"));
    tr.appendChild(cell(formatYen(item.price), "値段", "num"));
    tr.appendChild(cell(item.store, "購入店舗"));
    tr.appendChild(cell(item.note, "備考"));
    tr.appendChild(operationsCell(item));

    el.itemsBody.appendChild(tr);
  }
}

/** 期限切れ・期限間近・使い切みで行の色を変えるためのクラス名を決める(使い切みが最優先) */
function rowClass(item) {
  if (item.disposed) return "disposed";
  if (item.daysUntilExpiry === null) return "";
  if (item.daysUntilExpiry < 0) return "expired";
  if (item.daysUntilExpiry <= WARNING_DAYS) return "soon";
  return "";
}

// =====================================================================
// 3) ジャンルの色分け(バッジ)
// =====================================================================

// あらかじめ用意した4ジャンル+未分類には、決め打ちの色を割り当てる
const CATEGORY_COLORS = {
  "肉類": { bg: "#FBE7E3", fg: "#B23A2E" },
  "野菜類": { bg: "#E9F0E7", fg: "#3F7A4E" },
  "生鮮食品": { bg: "#E5F0F1", fg: "#2F6F73" },
  "調味料": { bg: "#FBF0DD", fg: "#C9832A" },
  "未分類": { bg: "#EDEAE1", fg: "#746C5C" },
};
// 設定ファイルで独自のジャンル名が追加された場合は、名前から自動で色を決める(候補の中から一定の色を選ぶ)
const CATEGORY_FALLBACK_PALETTE = [
  { bg: "#F0E6F5", fg: "#7A4E8C" },
  { bg: "#E6EEF5", fg: "#3E5C8C" },
  { bg: "#F5E9E6", fg: "#8C5A3E" },
  { bg: "#EAF5E6", fg: "#4E8C5E" },
];

/** ジャンル名から表示色を決める。同じ名前なら常に同じ色になる(ページを開き直しても変わらない) */
function categoryColor(name) {
  if (CATEGORY_COLORS[name]) return CATEGORY_COLORS[name];
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  }
  return CATEGORY_FALLBACK_PALETTE[hash % CATEGORY_FALLBACK_PALETTE.length];
}

/** ジャンル列のセル。色付きのバッジ(丸みのあるラベル)として表示する */
function categoryCell(categoryName) {
  const td = document.createElement("td");
  td.dataset.label = "ジャンル"; // スマホ表示で見出しとして出す文字
  const color = categoryColor(categoryName);
  const badge = document.createElement("span");
  badge.className = "category-badge";
  badge.style.background = color.bg;
  badge.style.color = color.fg;
  badge.textContent = categoryName;
  td.appendChild(badge);
  return td;
}

/** 「編集」「使い切った/取消」「削除」のアイコンボタンが並んだセルを作る */
function operationsCell(item) {
  const td = document.createElement("td");
  td.className = "ops";

  const wrap = document.createElement("div");
  wrap.className = "row-actions";
  wrap.appendChild(iconButton("edit", ICONS.edit, "編集する", () => openForm(item)));
  if (item.disposed) {
    wrap.appendChild(iconButton("restore", ICONS.restore, "使い切みを取り消す", () => restoreItem(item)));
  } else {
    wrap.appendChild(iconButton("dispose", ICONS.check, "使い切った", () => disposeItem(item)));
  }
  wrap.appendChild(iconButton("delete", ICONS.trash, "削除する", () => deleteItem(item)));

  td.appendChild(wrap);
  return td;
}

// =====================================================================
// 4) 追加・編集フォームの表示と保存
// =====================================================================

/** フォームを開く。item が null なら新規追加、指定があれば編集(値を埋める) */
function openForm(item) {
  el.formErrors.hidden = true;
  el.formTitle.textContent = item ? "食材を編集" : "食材を追加";
  el.itemId.value = item ? item.id : "";
  el.fieldName.value = item ? item.name : "";
  el.fieldCategory.value = item ? item.category : el.fieldCategory.options[0]?.value ?? "";
  el.fieldExpiry.value = item ? (item.expiry ?? "") : "";
  el.fieldPurchase.value = item ? (item.purchase ?? "") : todayString();
  el.fieldPrice.value = item ? item.price : "";
  el.fieldStore.value = item ? item.store : "";
  el.fieldNote.value = item ? item.note : "";

  el.formOverlay.hidden = false;
  // 開いた直後に「食材名」欄へフォーカスを移す(表示が終わってから当てないと効かないブラウザがあるため少し遅らせる)
  setTimeout(() => el.fieldName.focus(), 0);
}

function closeForm() {
  el.formOverlay.hidden = true;
}

/** フォームの「保存」。新規なら POST、編集なら PUT を呼ぶ */
async function onSubmitForm(event) {
  event.preventDefault(); // ブラウザ標準のページ送信を止める(fetchで送るため)

  const body = {
    name: el.fieldName.value.trim(),
    category: el.fieldCategory.value,
    expiry: el.fieldExpiry.value,
    purchase: el.fieldPurchase.value,
    price: el.fieldPrice.value === "" ? 0 : Number(el.fieldPrice.value),
    store: el.fieldStore.value.trim(),
    note: el.fieldNote.value.trim(),
  };

  const id = el.itemId.value;
  const url = id ? `/api/items/${id}` : "/api/items";
  const method = id ? "PUT" : "POST";

  const res = await fetch(url, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await res.json();

  if (!res.ok) {
    // 入力エラー・DBエラーは、フォームを閉じずに理由を表示する
    showFormErrors(data.errors ?? [data.error ?? "保存に失敗しました。"]);
    return;
  }

  closeForm();
  showToast(id ? `「${body.name}」を更新しました` : `「${body.name}」を追加しました`);
  await reloadList();
  addStoreOptionIfNew(body.store); // 新しい店名なら候補に足しておく
}

function showFormErrors(messages) {
  el.formErrors.innerHTML = "<ul>" + messages.map(m => `<li>${escapeHtml(m)}</li>`).join("") + "</ul>";
  el.formErrors.hidden = false;
}

/** 今回入力した店名が候補に無ければ、次回のために追加しておく(見た目だけの補助) */
function addStoreOptionIfNew(store) {
  if (!store) return;
  const exists = [...el.storeOptions.options].some(o => o.value === store);
  if (!exists) {
    el.storeOptions.appendChild(new Option(store));
  }
}

// =====================================================================
// 5) 使い切った/削除の操作
// =====================================================================

/** 「使い切った」ボタン: 使い切った日を今日にして、一覧から隠す */
async function disposeItem(item) {
  await fetch(`/api/items/${item.id}/dispose`, { method: "POST" });
  showToast(`「${item.name}」を使い切りました`);
  await reloadList();
}

/** 「使い切りを取消」ボタン: 使い切った状態を取り消し、一覧に戻す */
async function restoreItem(item) {
  await fetch(`/api/items/${item.id}/restore`, { method: "POST" });
  showToast(`「${item.name}」を一覧に戻しました`);
  await reloadList();
}

/** 「削除」ボタン: 確認してから完全に削除する */
async function deleteItem(item) {
  if (!confirm(`「${item.name}」を削除しますか？(元に戻せません)`)) {
    return;
  }
  await fetch(`/api/items/${item.id}`, { method: "DELETE" });
  showToast(`「${item.name}」を削除しました`);
  await reloadList();
}

// =====================================================================
// 6) 細かい補助関数
// =====================================================================

/** JSONを返すAPIを呼び出す共通処理 */
async function fetchJson(url) {
  const res = await fetch(url);
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error ?? "通信に失敗しました");
  }
  return data;
}

/** 表のセル(<td>)を1つ作る。label はスマホ表示のときに見出しとして出す文字 */
function cell(text, label, className) {
  const td = document.createElement("td");
  if (className) td.className = className;
  td.dataset.label = label;
  td.textContent = text ?? "";
  return td;
}

/** アイコン付きの小さな丸ボタンを作る */
function iconButton(kind, svgMarkup, title, onClick) {
  const b = document.createElement("button");
  b.type = "button";
  b.className = "icon-btn " + kind;
  b.title = title;
  b.setAttribute("aria-label", title);
  b.innerHTML = svgMarkup;
  b.addEventListener("click", onClick);
  return b;
}

/** 残り日数を「あと3日」「期限切れ(2日前)」のような表示用文字列にする */
function formatRemaining(days) {
  if (days === null || days === undefined) return "-";
  if (days < 0) return `期限切れ(${-days}日前)`;
  if (days === 0) return "今日まで";
  return `あと${days}日`;
}

function formatYen(price) {
  return `${Number(price).toLocaleString("ja-JP")}円`;
}

function todayString() {
  const d = new Date();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/** 操作結果のメッセージを、画面右上に数秒間だけ表示する */
let toastTimer = null;
function showToast(message, isError) {
  el.toast.textContent = message;
  el.toast.className = "toast" + (isError ? " error" : "");
  el.toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.toast.hidden = true; }, 3000);
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, ch => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
  }[ch]));
}

/** 操作アイコン用のSVG(手描きの線画アイコン。外部ライブラリは使っていない) */
const ICONS = {
  edit: '<svg viewBox="0 0 24 24" fill="none"><path d="M4 20h4L18.5 9.5a2.1 2.1 0 0 0-3-3L5 17v3Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>',
  check: '<svg viewBox="0 0 24 24" fill="none"><path d="M5 13l4.5 4.5L19 8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  restore: '<svg viewBox="0 0 24 24" fill="none"><path d="M4 10h9a5 5 0 1 1 0 10h-2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M8 5 4 10l4 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  trash: '<svg viewBox="0 0 24 24" fill="none"><path d="M5 7h14M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2m-8 0 1 12a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1l1-12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
};

// ページの読み込みが終わったら開始する
init();
