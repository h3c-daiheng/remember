const k = {
  page: "pv",
  btn: "btn-clk",
  nav: "nav",
  short: "short-key",
  area: "area-clk"
}, g = 50;
function b({ maxBatch: e, maxWaitMs: t, send: l }) {
  let c = [], u = null, a = null;
  function i() {
    u && (clearTimeout(u), u = null);
  }
  function d() {
    i(), u = setTimeout(() => {
      u = null, s();
    }, t);
  }
  async function s() {
    if (a)
      return await a, c.length > 0 ? s() : void 0;
    if (i(), c.length !== 0) {
      a = (async () => {
        for (; c.length > 0; ) {
          const r = Math.min(c.length, e), o = c.splice(0, r);
          if (o.length === 0)
            break;
          try {
            await l(o);
          } catch {
          }
        }
      })();
      try {
        await a;
      } finally {
        a = null;
      }
    }
  }
  function f(r) {
    c.push(r), c.length >= e ? s() : d();
  }
  function n() {
    i();
    const r = c;
    return c = [], r;
  }
  return { enqueue: f, flush: s, takeAllSync: n };
}
function m(e) {
  try {
    if (e == null)
      return "";
    if (typeof e == "string")
      return e.trim();
    if (typeof e == "object") {
      if ("success" in e && e.success === !1)
        return "";
      const t = e, l = t.data !== void 0 && t.data !== null ? t.data : t.ip !== void 0 ? t.ip : t.value;
      return l == null ? "" : String(l).trim();
    }
    return String(e).trim();
  } catch {
    return "";
  }
}
function S({ fetchIp: e }) {
  if (typeof e != "function")
    throw new Error("createIpResolver: fetchIp must be a function");
  return {
    async fetchIp() {
      try {
        const t = await e();
        return m(t);
      } catch {
        return "";
      }
    }
  };
}
function B(e) {
  const t = e.maxBatch ?? 10, l = e.maxWaitMs ?? 5e3, c = e.postBatch, u = e.resolveAbsoluteBatchUrl, a = typeof e.getExtraHeaders == "function" ? e.getExtraHeaders : () => ({}), i = b({ maxBatch: t, maxWaitMs: l, send: c });
  function d(n) {
    const r = [];
    for (let o = 0; o < n.length; o += g)
      r.push(n.slice(o, o + g));
    return r;
  }
  function s(n) {
    if (typeof window > "u" || !n || n.length === 0)
      return;
    const r = u(), o = {
      "Content-Type": "application/json",
      ...a()
    }, y = d(n);
    for (const v of y) {
      const p = JSON.stringify({ items: v });
      try {
        if (typeof fetch == "function") {
          fetch(r, {
            method: "POST",
            credentials: "include",
            keepalive: !0,
            headers: o,
            body: p
          }).catch(() => {
          });
          continue;
        }
      } catch {
      }
      try {
        if (typeof navigator < "u" && typeof Blob < "u") {
          const h = new Blob([p], { type: "application/json" });
          navigator.sendBeacon(r, h);
        }
      } catch {
      }
    }
  }
  function f() {
    i.flush().finally(() => {
      const n = i.takeAllSync();
      n.length && s(n);
    });
  }
  return typeof document < "u" && (document.addEventListener("visibilitychange", () => {
    document.visibilityState === "hidden" && f();
  }), window.addEventListener("pagehide", f), window.addEventListener("beforeunload", f)), {
    enqueue: (n) => i.enqueue(n),
    flush: () => i.flush(),
    takeAllSync: () => i.takeAllSync()
  };
}
export {
  g as MAX_SERVER_BATCH_ITEMS,
  k as TrackerType,
  b as createBatchQueue,
  B as createBrowserBatchTransport,
  S as createIpResolver,
  m as extractIpFromFetchResult
};
