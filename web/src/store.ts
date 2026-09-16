import { openDB, type IDBPDatabase } from "idb";
import { defaultData, normalizeData, type StudyNote, type WebAppData } from "./domain";
import { validateSchedule, type Schedule } from "./model";

const DB_NAME = "campus-schedule-web";
const STORES = ["meta", "schedule", "manualLessons", "tasks", "categories", "notes", "drafts", "focus", "achievements", "settings", "assets"] as const;
let connection: Promise<IDBPDatabase<any>> | undefined;

function db() {
  connection ??= openDB(DB_NAME, 2, {
    upgrade(database) {
      if (!database.objectStoreNames.contains("local")) database.createObjectStore("local");
      for (const name of STORES) if (!database.objectStoreNames.contains(name)) database.createObjectStore(name);
    },
  });
  return connection;
}

async function readArray<T>(database: IDBPDatabase<any>, store: string): Promise<T[]> {
  return (await database.get(store, "all")) ?? [];
}

export async function readData(): Promise<WebAppData> {
  const database = await db();
  const meta = await database.get("meta", "app");
  if (!meta) {
    const legacy = await database.get("local", "schedule");
    const fresh = defaultData();
    if (legacy) fresh.schedule = validateSchedule(legacy, legacy.firstMonday);
    await saveData(fresh);
    return fresh;
  }
  const [schedule, manualLessons, tasks, categories, notes, focus, achievements, settings] = await Promise.all([
    database.get("schedule", "current"), readArray(database, "manualLessons"), readArray(database, "tasks"),
    readArray(database, "categories"), readArray(database, "notes"), database.get("focus", "state"),
    database.get("achievements", "state"), database.get("settings", "state"),
  ]);
  return normalizeData({
    ...meta, schedule: schedule ? validateSchedule(schedule, schedule.firstMonday) : null,
    manualLessons, tasks, categories, notes,
    focusSessions: focus?.focusSessions ?? [], activeFocus: focus?.activeFocus ?? null,
    achievements: achievements?.achievements ?? [], featuredAchievementIds: achievements?.featuredAchievementIds ?? [],
    profile: settings?.profile, settings: settings?.settings,
  });
}

export async function saveData(data: WebAppData) {
  const database = await db();
  const normalized = normalizeData({ ...data, updatedAt: new Date().toISOString() });
  const tx = database.transaction(["meta", "schedule", "manualLessons", "tasks", "categories", "notes", "focus", "achievements", "settings"], "readwrite");
  await Promise.all([
    tx.objectStore("meta").put({ format: normalized.format, version: normalized.version, updatedAt: normalized.updatedAt }, "app"),
    normalized.schedule ? tx.objectStore("schedule").put(normalized.schedule, "current") : tx.objectStore("schedule").delete("current"),
    tx.objectStore("manualLessons").put(normalized.manualLessons, "all"),
    tx.objectStore("tasks").put(normalized.tasks, "all"),
    tx.objectStore("categories").put(normalized.categories, "all"),
    tx.objectStore("notes").put(normalized.notes, "all"),
    tx.objectStore("focus").put({ focusSessions: normalized.focusSessions, activeFocus: normalized.activeFocus }, "state"),
    tx.objectStore("achievements").put({ achievements: normalized.achievements, featuredAchievementIds: normalized.featuredAchievementIds }, "state"),
    tx.objectStore("settings").put({ profile: normalized.profile, settings: normalized.settings }, "state"),
    tx.done,
  ]);
  try { await navigator.storage?.persist?.(); } catch { /* Saving does not depend on persistent grant. */ }
  return normalized;
}

export async function readSchedule() { return (await readData()).schedule; }
export async function saveSchedule(schedule: Schedule) {
  const data = await readData(); data.schedule = validateSchedule(schedule, schedule.firstMonday); await saveData(data);
}
export async function saveDraft(note: StudyNote) { await (await db()).put("drafts", { note, savedAt: new Date().toISOString() }, note.id); }
export async function readDraft(id: string): Promise<{ note: StudyNote; savedAt: string } | undefined> { return (await db()).get("drafts", id); }
export async function deleteDraft(id: string) { await (await db()).delete("drafts", id); }
export async function putAsset(id: string, blob: Blob) { await (await db()).put("assets", blob, id); return id; }
export async function readAsset(id?: string): Promise<Blob | undefined> { return id ? (await db()).get("assets", id) : undefined; }
export async function deleteAsset(id?: string) { if (id) await (await db()).delete("assets", id); }

export async function exportBackup() {
  const data = await readData(); const database = await db(); const assets: Record<string, string> = {};
  for (const key of await database.getAllKeys("assets")) {
    const blob = await database.get("assets", key);
    if (blob instanceof Blob && blob.size <= 3_000_000) assets[String(key)] = await blobToDataUrl(blob);
  }
  return { ...data, assets };
}
export async function importBackup(raw: unknown) {
  if (!raw || typeof raw !== "object") throw new Error("备份文件结构不正确");
  const source = raw as Partial<WebAppData> & { assets?: Record<string, string> };
  if (source.format !== "campus-schedule-backup" || source.version !== 2) throw new Error("请选择“中大课表完整备份 V2”文件");
  if (JSON.stringify(source).length > 25_000_000) throw new Error("备份文件超过 25 MB");
  const data = normalizeData(source);
  if (data.schedule) data.schedule = validateSchedule(data.schedule, data.schedule.firstMonday);
  await saveData(data);
  if (source.assets) for (const [id, url] of Object.entries(source.assets)) {
    if (!url.startsWith("data:image/") || url.length > 5_000_000) continue;
    await putAsset(id, await (await fetch(url)).blob());
  }
  return data;
}
function blobToDataUrl(blob: Blob) { return new Promise<string>((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.onerror = () => reject(reader.error); reader.readAsDataURL(blob); }); }
