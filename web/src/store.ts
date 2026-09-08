import { openDB } from "idb";
import { validateSchedule, type Schedule } from "./model";
const db = () =>
  openDB("campus-schedule-web", 1, {
    upgrade(db) {
      db.createObjectStore("local");
    },
  });
export async function readSchedule() {
  const d = await db();
  const s = await d.get("local", "schedule");
  return s ? validateSchedule(s, s.firstMonday) : null;
}
export async function saveSchedule(s: Schedule) {
  const d = await db();
  await d.put("local", validateSchedule(s, s.firstMonday), "schedule");
  try {
    await navigator.storage?.persist?.();
  } catch {
    /* Saving does not depend on persistence grant. */
  }
}
