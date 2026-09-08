import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { useRegisterSW } from "virtual:pwa-register/react";
import {
  addDays,
  lastWeek,
  lessons,
  monday,
  parsePayload,
  today,
  weekOf,
  type Result,
  type Schedule,
} from "./model";
import { parseWord } from "./word";
import { readSchedule, saveSchedule } from "./store";
import "./style.css";
const base = import.meta.env.BASE_URL;
const school =
  "https://jwxt.sysu.edu.cn/jwxt/mk/schedule-web/#/studentTimeTabPrint?code=jwxsd_xskbcx";
function Modal({
  title,
  children,
  close,
}: {
  title: string;
  children: React.ReactNode;
  close: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
  }, []);
  return (
    <dialog
      ref={ref}
      onCancel={(e) => {
        e.preventDefault();
        close();
      }}
    >
      <header>
        <h2>{title}</h2>
        <button className="quiet" onClick={close} aria-label="关闭">
          关闭
        </button>
      </header>
      {children}
    </dialog>
  );
}
function App() {
  const [tab, setTab] = useState("今日"),
    [schedule, setSchedule] = useState<Schedule | null>(null),
    [date, setDate] = useState("2026-09-07"),
    [week, setWeek] = useState(1),
    [ready, setReady] = useState(false),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [preview, setPreview] = useState<Result | null>(null),
    [paste, setPaste] = useState(false),
    [text, setText] = useState(""),
    [install, setInstall] = useState(false),
    [detail, setDetail] = useState<ReturnType<typeof lessons>[number] | null>(
      null,
    ),
    [now, setNow] = useState(Date.now()),
    [online, setOnline] = useState(navigator.onLine),
    [prompt, setPrompt] = useState<any>(null),
    [standalone, setStandalone] = useState(
      matchMedia("(display-mode: standalone)").matches ||
        (navigator as any).standalone === true,
    );
  const [cachedReady, setCachedReady] = useState(false);
  const lock = useRef(false);
  const {
    offlineReady: [offline],
    needRefresh: [update],
    updateServiceWorker,
  } = useRegisterSW({
    onRegisteredSW(_url, r) {
      if (r?.active) setCachedReady(true);
    },
    onRegisterError() {
      setMessage("离线资源尚未准备好，请联网后重新打开。");
    },
  });
  useEffect(() => {
    if ("serviceWorker" in navigator)
      navigator.serviceWorker.ready.then(() => setCachedReady(true));
    readSchedule()
      .then((s) => {
        setSchedule(s);
        if (s) {
          setDate(s.firstMonday);
          setWeek(Math.max(1, Math.min(lastWeek(s), weekOf(s, today()))));
        }
      })
      .catch(() =>
        setMessage(
          "无法读取本机课表。请检查浏览器存储设置；原数据未主动删除。",
        ),
      )
      .finally(() => setReady(true));
    const timer = setInterval(() => setNow(Date.now()), 30000);
    const network = () => setOnline(navigator.onLine);
    const add = (e: Event) => {
      e.preventDefault();
      setPrompt(e);
    };
    const installed = () => {
      setStandalone(true);
      setPrompt(null);
    };
    window.addEventListener("online", network);
    window.addEventListener("offline", network);
    window.addEventListener("beforeinstallprompt", add);
    window.addEventListener("appinstalled", installed);
    return () => {
      clearInterval(timer);
      window.removeEventListener("online", network);
      window.removeEventListener("offline", network);
      window.removeEventListener("beforeinstallprompt", add);
      window.removeEventListener("appinstalled", installed);
    };
  }, []);
  async function run(action: () => Promise<void> | void) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setMessage("");
    try {
      await action();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "操作未完成，请重试。");
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  async function file(f: File) {
    await run(async () => {
      monday(date);
      if (f.size > 8 * 1024 * 1024)
        throw new Error("文件超过 8 MB，请使用原始课表文件");
      const bytes = new Uint8Array(await f.arrayBuffer());
      let result: Result;
      if (new TextDecoder().decode(bytes.slice(0, 5)) === "%PDF-") {
        result = await (await import("./pdf")).parsePdf(bytes, date);
      } else if (f.name.endsWith(".json"))
        result = parsePayload(new TextDecoder().decode(bytes), date);
      else result = parseWord(bytes, date);
      setPreview(result);
    });
  }
  const currentDate = today(),
    dayLessons = schedule ? lessons(schedule, currentDate) : [],
    clock = new Intl.DateTimeFormat("en-GB", {
      timeZone: "Asia/Shanghai",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).format(now),
    next = dayLessons.find((l) => l.end > clock);
  const conflicts = (
    ls: ReturnType<typeof lessons>,
    l: ReturnType<typeof lessons>[number],
  ) => ls.some((o) => o.id !== l.id && o.start < l.end && o.end > l.start);
  const card = (
    l: ReturnType<typeof lessons>[number],
    ls: ReturnType<typeof lessons>,
    feature = false,
  ) => (
    <button
      key={l.id}
      className={"lesson " + (feature ? "feature" : "")}
      onClick={() => setDetail(l)}
    >
      <span className="lesson-time">
        {l.start}
        <small>— {l.end}</small>
      </span>
      <span className="lesson-content">
        <strong>{l.title}</strong>
        <span>
          {[...new Set(l.candidates.map((c) => c.location || "地点待定"))].join(
            " / ",
          )}
        </span>
        <small>
          第 {l.startPeriod}–{l.endPeriod} 节
          {l.candidates.length > 1 ? " · 候选安排待确认" : ""}
          {conflicts(ls, l) ? " · 时间冲突" : ""}
        </small>
      </span>
      <span aria-hidden="true">↗</span>
    </button>
  );
  return (
    <>
      <div className="app">
        <header className="masthead">
          <a href={base} className="brand">
            <span className="brand-icon" aria-hidden="true">
              课
            </span>
            <span>
              中大课表<small>xiaoyle 制作</small>
            </span>
          </a>
          <span className="connection">
            {!online
              ? "离线查看"
              : offline || cachedReady
                ? "已可离线使用"
                : "正在准备离线资源"}
          </span>
        </header>
        <main>
          <div className="page-title">
            <div>
              <p className="eyebrow">
                {tab === "今日"
                  ? new Intl.DateTimeFormat("zh-CN", {
                      timeZone: "Asia/Shanghai",
                      month: "long",
                      day: "numeric",
                      weekday: "long",
                    }).format(now)
                  : "中大课表 · 网页版"}
              </p>
              <h1>{tab === "今日" ? "今天，去哪里上课" : tab}</h1>
            </div>
            {tab === "今日" && schedule && (
              <span className="week-badge">
                第 {weekOf(schedule, currentDate)} 周
              </span>
            )}
          </div>
          {message && (
            <div className="notice" role="alert">
              {message}
              <button className="quiet" onClick={() => setMessage("")}>
                关闭提示
              </button>
            </div>
          )}
          {busy && <p role="status">正在处理，请稍候…</p>}
          {update && (
            <div className="notice">
              新版本已准备好。
              <button
                disabled={busy || !!preview}
                onClick={() => updateServiceWorker(true)}
              >
                刷新使用新版
              </button>
            </div>
          )}
          {!ready ? (
            <p role="status">正在读取本机课表…</p>
          ) : tab !== "导入与设置" && !schedule ? (
            <section className="empty">
              <div className="empty-calendar" aria-hidden="true">
                周一<big>07</big>从这一周开始
              </div>
              <h2>把下一节课，放在手边</h2>
              <p>
                导入学校课表，时间、教室和每周安排都在这里。保存后，断网也能查看。
              </p>
              <button className="primary" onClick={() => setTab("导入与设置")}>
                导入我的课表
              </button>
              {!standalone && (
                <button className="secondary" onClick={() => setInstall(true)}>
                  先添加到主屏幕
                </button>
              )}
            </section>
          ) : tab === "今日" ? (
            <>
              <section>
                <div className="section-heading">
                  <h2>
                    {next
                      ? next.start <= clock
                        ? "正在上课"
                        : "下一节课"
                      : "今日安排"}
                  </h2>
                  <span>北京时间</span>
                </div>
                {next ? (
                  card(next, dayLessons, true)
                ) : (
                  <div className="empty small">
                    <h2>
                      {schedule &&
                      weekOf(schedule, currentDate) > lastWeek(schedule)
                        ? "本学期已结束"
                        : schedule && weekOf(schedule, currentDate) < 1
                          ? "新学期尚未开始"
                          : dayLessons.length
                            ? "今天的课程已结束"
                            : "今天没有课程"}
                    </h2>
                    <p>可以在「课表」查看其他日期的安排。</p>
                  </div>
                )}
              </section>
              <section>
                <div className="section-heading">
                  <h2>今日课程</h2>
                  <span>{dayLessons.length} 项安排</span>
                </div>
                {dayLessons.map((l) => card(l, dayLessons))}
              </section>
            </>
          ) : tab === "课表" && schedule ? (
            <>
              <div className="week-picker">
                <button
                  disabled={week <= 1}
                  onClick={() => setWeek(week - 1)}
                  aria-label="上一周"
                >
                  ←
                </button>
                <label>
                  教学周
                  <select
                    value={week}
                    onChange={(e) => setWeek(+e.target.value)}
                  >
                    {Array.from({ length: lastWeek(schedule) }, (_, i) => (
                      <option key={i} value={i + 1}>
                        第 {i + 1} 周
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  disabled={week >= lastWeek(schedule)}
                  onClick={() => setWeek(week + 1)}
                  aria-label="下一周"
                >
                  →
                </button>
              </div>
              {Array.from({ length: 7 }, (_, i) => {
                const d = addDays(schedule.firstMonday, (week - 1) * 7 + i),
                  ls = lessons(schedule, d);
                return (
                  <section key={d}>
                    <div className="section-heading">
                      <h2>星期{"一二三四五六日"[i]}</h2>
                      <span>{d.slice(5)}</span>
                    </div>
                    {ls.length ? (
                      ls.map((l) => card(l, ls))
                    ) : (
                      <p className="no-class">这天没有课程</p>
                    )}
                  </section>
                );
              })}
            </>
          ) : tab === "导入与设置" ? (
            <>
              <section className="panel">
                <h2>学校课表</h2>
                <p>{schedule?.term || "还没有导入课表"} · 仅保存在此设备</p>
                <label>
                  第 1 教学周的周一
                  <input
                    type="date"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                  />
                </label>
                <p className="help">
                  日期用于下一次导入；不会修改当前已保存课表。
                </p>
                <label
                  className={"file-button primary " + (busy ? "disabled" : "")}
                >
                  从文件导入课表
                  <input
                    aria-label="选择课表文件"
                    type="file"
                    accept=".doc,.docx,.pdf,.json,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/json"
                    disabled={busy}
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      e.target.value = "";
                      if (f) file(f);
                    }}
                  />
                </label>
                <p className="help">
                  Word / 文字 PDF /
                  课表备份。聊天中的文件请先存储到「文件」或「下载」。扫描件暂不支持。
                </p>
              </section>
              <section className="panel">
                <span className="eyebrow">SAFARI + 快捷指令</span>
                <h2>从学校网页带回课表</h2>
                <ol>
                  <li>在 Safari 登录学校，打开完整课表并选择「全部」。</li>
                  <li>分享 → 运行「中大课表提取」，复制课程数据。</li>
                  <li>回到主屏幕应用，粘贴并核对后保存。</li>
                </ol>
                <div className="actions">
                  <a
                    className="secondary"
                    href="https://jwxt.sysu.edu.cn/jwxt/#/login"
                    target="_blank"
                    rel="noreferrer"
                  >
                    打开学校登录 ↗
                  </a>
                  <a
                    className="secondary"
                    href={school}
                    target="_blank"
                    rel="noreferrer"
                  >
                    打开完整课表 ↗
                  </a>
                </div>
                <button
                  className="primary"
                  disabled={busy}
                  onClick={() => {
                    setPaste(true);
                    setText("");
                    navigator.clipboard
                      ?.readText()
                      .then(setText)
                      .catch(() => {});
                  }}
                >
                  粘贴课表
                </button>
                <a
                  className="text-link"
                  href={base + "shortcut-guide.html"}
                  target="_blank"
                  rel="noreferrer"
                >
                  首次使用：配置快捷指令 ↗
                </a>
                <p className="help">
                  需在 Safari 中操作；学校登录和快捷指令的完整流程待 iPhone
                  真机验证。账号与验证码仅在学校页面输入。
                </p>
              </section>
              <section className="panel">
                <h2>主屏幕与本机数据</h2>
                {!standalone ? (
                  <button
                    className="secondary"
                    onClick={() => setInstall(true)}
                  >
                    添加到主屏幕
                  </button>
                ) : (
                  <p>已在独立 Web App 中打开。</p>
                )}
                <p>
                  推荐先添加，再导入。Safari
                  中的课表可能需要在主屏幕应用中重新导入。
                </p>
                <button
                  className="secondary"
                  disabled={!schedule || busy}
                  onClick={() => {
                    if (!schedule) return;
                    const blob = new Blob(
                        [
                          JSON.stringify({
                            format: "campus-schedule",
                            version: 1,
                            schedule,
                          }),
                        ],
                        { type: "application/json" },
                      ),
                      url = URL.createObjectURL(blob),
                      a = document.createElement("a");
                    a.href = url;
                    a.download = "中大课表备份.json";
                    a.click();
                    setTimeout(() => URL.revokeObjectURL(url), 60000);
                  }}
                >
                  导出课表备份
                </button>
                <p className="help">
                  清除网站数据或卸载主屏幕应用可能丢失本机课表，请保留原文件或备份。短暂断网可查看已保存课表；首次使用需联网准备资源。
                </p>
              </section>
              <footer>
                xiaoyle 制作 · 非中山大学官方应用
                <br />
                网页版 0.1.0 · 无账号、无广告、无云同步
                <br />
                文件、剪贴板课表不上传；网站托管方会处理普通访问请求。
              </footer>
            </>
          ) : null}
        </main>
      </div>
      <nav className="bottom-nav" aria-label="主要导航">
        {["今日", "课表", "导入与设置"].map((t, i) => (
          <button
            key={t}
            aria-current={tab === t ? "page" : undefined}
            onClick={() => {
              setTab(t);
              window.scrollTo(0, 0);
            }}
          >
            <span aria-hidden="true">{["◷", "▦", "☷"][i]}</span>
            {t}
          </button>
        ))}
      </nav>
      {preview && (
        <Modal
          title="确认导入"
          close={() => {
            if (!busy) setPreview(null);
          }}
        >
          <p>{preview.schedule.term}</p>
          <p>
            <strong>
              {preview.schedule.rules.length} 项安排 ·{" "}
              {lastWeek(preview.schedule)} 周
            </strong>
            <br />第 1 周周一：{preview.schedule.firstMonday}
          </p>
          {preview.warnings.map((w) => (
            <p className="help" key={w}>
              {w}
            </p>
          ))}
          {schedule && <p className="notice">确认后替换当前已保存课表。</p>}
          {message && <p role="alert">{message}</p>}
          <div className="preview-list">
            {preview.schedule.rules.map((r) => (
              <div key={r.id}>
                <strong>{r.title}</strong>
                <small>
                  星期{"一二三四五六日"[r.weekday - 1]} · 第 {r.startPeriod}–
                  {r.endPeriod} 节
                </small>
              </div>
            ))}
          </div>
          <button
            className="primary"
            disabled={busy}
            onClick={() =>
              run(async () => {
                await saveSchedule(preview.schedule);
                setSchedule(preview.schedule);
                setDate(preview.schedule.firstMonday);
                setWeek(
                  Math.max(
                    1,
                    Math.min(
                      lastWeek(preview.schedule),
                      weekOf(preview.schedule, today()),
                    ),
                  ),
                );
                setPreview(null);
                setPaste(false);
                setText("");
                setMessage("课表已保存到本机。");
                setTab("今日");
              })
            }
          >
            确认保存课表
          </button>
        </Modal>
      )}
      {paste && !preview && (
        <Modal title="粘贴学校课表" close={() => setPaste(false)}>
          <p>
            粘贴快捷指令复制的课表数据。若系统未允许读取剪贴板，请长按输入框粘贴。
          </p>
          <textarea
            className="resize-none"
            aria-label="课表数据"
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={2000000}
            placeholder="在这里粘贴课表数据"
          />
          <button
            className="primary"
            disabled={busy || !text.trim()}
            onClick={() =>
              run(() => {
                setPreview(parsePayload(text, date));
              })
            }
          >
            读取并预览
          </button>
          {message && <p role="alert">{message}</p>}
        </Modal>
      )}
      {install && (
        <Modal title="把课表放到主屏幕" close={() => setInstall(false)}>
          <ol>
            <li>用 Safari 打开本网站。</li>
            <li>点击浏览器「分享」，选择「添加到主屏幕」。</li>
            <li>名称保留「中大课表」；如有「作为 Web App 打开」，请开启。</li>
            <li>点击「添加」，回到桌面从图标打开，再导入课表。</li>
          </ol>
          {prompt && (
            <button
              className="primary"
              onClick={async () => {
                await prompt.prompt();
                setPrompt(null);
              }}
            >
              打开系统安装窗口
            </button>
          )}
          <p className="help">
            Android
            可使用浏览器菜单中的「安装应用／添加到主屏幕」。电脑可使用地址栏安装按钮。微信内请先选择在系统浏览器中打开。
          </p>
        </Modal>
      )}
      {detail && (
        <Modal title={detail.title} close={() => setDetail(null)}>
          <p className="detail-time">
            {detail.start} — {detail.end}
          </p>
          <p>
            第 {detail.startPeriod}–{detail.endPeriod} 节
          </p>
          {detail.candidates.length > 1 && (
            <p className="notice">多个候选安排待确认，请以学校通知为准。</p>
          )}
          {detail.candidates.map((c, i) => (
            <div className="candidate" key={i}>
              <strong>{c.location || "地点待定"}</strong>
              <p>{c.teacher || "未提供教师"}</p>
            </div>
          ))}
        </Modal>
      )}
    </>
  );
}
createRoot(document.getElementById("root")!).render(<App />);
