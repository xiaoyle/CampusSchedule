import { allLessons, taskOccurrences, type WebAppData } from "./domain";
import { addDays, lastWeek } from "./model";

const esc = (s: string) => s.replace(/\\/g, "\\\\").replace(/\n/g, "\\n").replace(/,/g, "\\,").replace(/;/g, "\\;");
const utc = (local: string) => {
  const d = new Date(`${local}:00+08:00`);
  return d.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z");
};
const event = (id: string, title: string, start: string, end: string, location: string, description: string, alarmMinutes: number) => [
  "BEGIN:VEVENT", `UID:${id}@xiaoyle-campus-schedule`, `DTSTAMP:${utc(new Date().toISOString().slice(0, 16))}`,
  `DTSTART:${utc(start)}`, `DTEND:${utc(end)}`, `SUMMARY:${esc(title)}`, `LOCATION:${esc(location)}`, `DESCRIPTION:${esc(description)}`,
  ...(alarmMinutes >= 0 ? ["BEGIN:VALARM", `TRIGGER:-PT${alarmMinutes}M`, "ACTION:DISPLAY", `DESCRIPTION:${esc(title)}`, "END:VALARM"] : []), "END:VEVENT",
].join("\r\n");

export function createCalendar(data: WebAppData, courseAlarm = 10) {
  const from = data.schedule?.firstMonday ?? new Date().toISOString().slice(0, 10);
  const weeks = data.schedule ? lastWeek(data.schedule) : 18;
  const to = addDays(from, weeks * 7 + 14);
  const rows = allLessons(data, from, to).map((x) => event(x.key, x.title, `${x.date}T${x.start}`, `${x.date}T${x.end}`, x.location, x.teacher || "中大课表", courseAlarm));
  rows.push(...taskOccurrences(data.tasks, from, to).filter((x) => x.actualDueAt && !x.occurrenceCompletedAt).map((x) => {
    const start = x.actualDueAt!; const endDate = new Date(`${start}:00+08:00`); endDate.setMinutes(endDate.getMinutes() + 30);
    const end = `${endDate.getFullYear()}-${String(endDate.getMonth() + 1).padStart(2, "0")}-${String(endDate.getDate()).padStart(2, "0")}T${String(endDate.getHours()).padStart(2, "0")}:${String(endDate.getMinutes()).padStart(2, "0")}`;
    return event(`task-${x.occurrenceKey}`, `待办 · ${x.title}`, start, end, x.courseTitle, x.note, x.remindBeforeMinutes ?? -1);
  }));
  return ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//xiaoyle//CampusSchedule//ZH-CN", "CALSCALE:GREGORIAN", "METHOD:PUBLISH", "X-WR-CALNAME:中大课表", "X-WR-TIMEZONE:Asia/Shanghai", ...rows, "END:VCALENDAR", ""].join("\r\n");
}

export function downloadCalendar(data: WebAppData) {
  const blob = new Blob([createCalendar(data)], { type: "text/calendar;charset=utf-8" });
  const file = new File([blob], "中大课表与待办.ics", { type: blob.type });
  if (navigator.share && navigator.canShare?.({ files: [file] })) return navigator.share({ title: "导入 Apple 日历", files: [file] });
  const url = URL.createObjectURL(blob), a = document.createElement("a"); a.href = url; a.download = file.name; a.click(); setTimeout(() => URL.revokeObjectURL(url), 60000);
  return Promise.resolve();
}
