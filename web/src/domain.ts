import { addDays, lastWeek, lessons, today, weekOf, type Candidate, type Schedule } from "./model";

export type ThemeMode = "campus" | "ocean" | "season";
export type TaskType = "HOMEWORK" | "EXAM" | "REVIEW" | "OTHER";
export type TaskPriority = "NORMAL" | "IMPORTANT" | "URGENT";
export type RepeatKind = "DAILY" | "WEEKLY" | "CUSTOM_WEEKDAYS";
export type NoteMode = "PLAIN" | "MARKDOWN";
export type PaperStyle = "CLEAN" | "RULED" | "GRID" | "DOT" | "WARM";
export type FocusMode = "COUNTDOWN" | "STOPWATCH" | "BREAK";
export type FocusStatus = "RUNNING" | "PAUSED" | "COMPLETED" | "STOPPED";

export interface ManualLessonRevision {
  effectiveIndex: number;
  date: string;
  title: string;
  start: string;
  end: string;
  teacher: string;
  location: string;
  color: string;
}
export interface ManualLessonEdit extends Partial<Omit<ManualLessonRevision, "effectiveIndex">> {
  index: number;
  cancelled?: boolean;
}
export interface ManualLesson {
  id: string;
  date: string;
  title: string;
  start: string;
  end: string;
  teacher: string;
  location: string;
  color: string;
  repeatCount: number;
  revisions: ManualLessonRevision[];
  instanceEdits: ManualLessonEdit[];
}
export interface StudySubtask { id: string; title: string; order: number }
export interface TaskRepeatRule { kind: RepeatKind; weekdays: number[]; endsOn?: string }
export interface TaskInstanceState {
  occurrenceKey: string;
  completedAt?: string;
  completedSubtaskIds: string[];
  deleted?: boolean;
}
export interface StudyTask {
  id: string;
  title: string;
  type: TaskType;
  courseRuleId?: string;
  courseTitle: string;
  dueAt?: string;
  priority: TaskPriority;
  note: string;
  remindBeforeMinutes?: number;
  completedAt?: string;
  createdAt: string;
  subtasks: StudySubtask[];
  repeatRule?: TaskRepeatRule;
  instanceStates: TaskInstanceState[];
  estimatedMinutes?: number;
}
export interface NoteCategory { id: string; title: string; color: string; order: number }
export interface NoteAppearance {
  paper: PaperStyle;
  accent: string;
  font: "SYSTEM" | "ROUNDED" | "SERIF";
  fontScale: number;
  lineSpacing: number;
  pagePadding: number;
  patternAlpha: number;
}
export interface StudyNote {
  id: string;
  categoryId: string;
  title: string;
  content: string;
  pinned: boolean;
  createdAt: string;
  updatedAt: string;
  contentMode: NoteMode;
  appearance?: NoteAppearance;
}
export interface FocusSession {
  id: string;
  mode: FocusMode;
  title: string;
  taskId?: string;
  startedAt: string;
  endedAt: string;
  focusedSeconds: number;
  plannedSeconds?: number;
  status: FocusStatus;
}
export interface ActiveFocus {
  id: string;
  mode: FocusMode;
  title: string;
  taskId?: string;
  plannedSeconds?: number;
  accumulatedSeconds: number;
  runStartedAt: string;
  startedAt: string;
  status: "RUNNING" | "PAUSED";
}
export interface BadgeDesign {
  shape: "CIRCLE" | "SHIELD" | "HEXAGON" | "DIAMOND";
  primary: string;
  accent: string;
  icon: string;
  glyph: string;
}
export interface CustomAchievement {
  id: string;
  name: string;
  description: string;
  metric: "TOTAL_FOCUS_MINUTES" | "SINGLE_FOCUS_MINUTES" | "FOCUS_STREAK_DAYS" | "COMPLETED_TASKS" | "ON_TIME_TASKS" | "MANUAL";
  target: number;
  unlockedAt?: string;
  badge: BadgeDesign;
}
export interface ProfileData {
  nickname: string;
  greeting: string;
  netId: string;
  studentId: string;
  dorm: string;
  avatarAssetId?: string;
}
export interface LaunchStyle {
  enabled: boolean;
  scene: "core" | "orbit" | "kapok" | "custom";
  backgroundAssetId?: string;
  coreColor: string;
  haloColor: string;
  opacity: number;
  intensity: number;
}
export interface AppSettings {
  theme: ThemeMode;
  darkMode: "system" | "light" | "dark";
  weeklyTaskTarget: number;
  weeklyFocusTarget: number;
  noteStyle: NoteAppearance;
  launch: LaunchStyle;
  firstMonday: string;
  pushEndpoint: string;
  pushPublicKey: string;
}
export interface WebAppData {
  format: "campus-schedule-backup";
  version: 2;
  updatedAt: string;
  schedule: Schedule | null;
  manualLessons: ManualLesson[];
  tasks: StudyTask[];
  categories: NoteCategory[];
  notes: StudyNote[];
  focusSessions: FocusSession[];
  activeFocus: ActiveFocus | null;
  achievements: CustomAchievement[];
  featuredAchievementIds: string[];
  profile: ProfileData;
  settings: AppSettings;
}

export interface LessonOccurrence {
  key: string;
  ruleId: string;
  date: string;
  title: string;
  start: string;
  end: string;
  teacher: string;
  location: string;
  candidates: Candidate[];
  manual: boolean;
  color?: string;
  manualIndex?: number;
  repeatCount?: number;
  cancelled?: boolean;
}
export interface TaskOccurrence extends StudyTask {
  occurrenceKey: string;
  occurrenceDate?: string;
  actualDueAt?: string;
  occurrenceCompletedAt?: string;
}

export const defaultNoteAppearance: NoteAppearance = {
  paper: "CLEAN", accent: "#176B52", font: "SYSTEM", fontScale: 1,
  lineSpacing: 1.55, pagePadding: 20, patternAlpha: .22,
};
export const defaultData = (): WebAppData => ({
  format: "campus-schedule-backup",
  version: 2,
  updatedAt: new Date().toISOString(),
  schedule: null,
  manualLessons: [],
  tasks: [],
  categories: [{ id: "inbox", title: "灵感收集", color: "#176B52", order: 0 }],
  notes: [],
  focusSessions: [],
  activeFocus: null,
  achievements: [],
  featuredAchievementIds: [],
  profile: { nickname: "同学", greeting: "今天，也向前一步", netId: "", studentId: "", dorm: "" },
  settings: {
    theme: "campus", darkMode: "system", weeklyTaskTarget: 5, weeklyFocusTarget: 300,
    noteStyle: defaultNoteAppearance,
    launch: { enabled: false, scene: "core", coreColor: "#8BF2C8", haloColor: "#4AA8FF", opacity: .86, intensity: .75 },
    firstMonday: "2026-09-07", pushEndpoint: "", pushPublicKey: "",
  },
});

export const uid = (prefix: string) => `${prefix}-${crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`}`;
export const shanghaiDate = () => today();
export const shanghaiNow = () => new Date(new Date().toLocaleString("en-US", { timeZone: "Asia/Shanghai" }));
export const isoLocal = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}T${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
export const dateLabel = (date: string, includeWeekday = true) => new Intl.DateTimeFormat("zh-CN", { timeZone: "Asia/Shanghai", month: "long", day: "numeric", ...(includeWeekday ? { weekday: "short" } : {}) }).format(new Date(`${date}T12:00:00+08:00`));
export const timeLabel = (value?: string) => value ? new Intl.DateTimeFormat("zh-CN", { timeZone: "Asia/Shanghai", month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(`${value}:00+08:00`)) : "无截止日期";

function revisionFor(item: ManualLesson, index: number) {
  return [...item.revisions].filter((r) => r.effectiveIndex <= index).sort((a, b) => b.effectiveIndex - a.effectiveIndex)[0];
}
export function manualOccurrences(items: ManualLesson[], from?: string, to?: string): LessonOccurrence[] {
  const out: LessonOccurrence[] = [];
  for (const item of items) for (let index = 0; index < Math.max(1, item.repeatCount); index++) {
    const revision = revisionFor(item, index);
    const edit = item.instanceEdits.find((e) => e.index === index);
    const baseDate = revision?.date ?? item.date;
    const date = edit?.date ?? addDays(baseDate, (index - (revision?.effectiveIndex ?? 0)) * 7);
    if ((from && date < from) || (to && date > to)) continue;
    const title = edit?.title ?? revision?.title ?? item.title;
    const teacher = edit?.teacher ?? revision?.teacher ?? item.teacher;
    const location = edit?.location ?? revision?.location ?? item.location;
    out.push({
      key: `manual@${item.id}@${index}`, ruleId: item.id, date, title,
      start: edit?.start ?? revision?.start ?? item.start,
      end: edit?.end ?? revision?.end ?? item.end,
      teacher, location, candidates: [{ teacher, location }], manual: true,
      color: edit?.color ?? revision?.color ?? item.color, manualIndex: index,
      repeatCount: item.repeatCount, cancelled: edit?.cancelled,
    });
  }
  return out.sort((a, b) => a.date.localeCompare(b.date) || a.start.localeCompare(b.start));
}
export function scheduleOccurrences(schedule: Schedule | null, from: string, to: string): LessonOccurrence[] {
  if (!schedule) return [];
  const result: LessonOccurrence[] = [];
  for (let d = from; d <= to; d = addDays(d, 1)) for (const item of lessons(schedule, d)) {
    const first = item.candidates[0] ?? { teacher: "", location: "" };
    result.push({ key: `${item.id}@${d}`, ruleId: item.id, date: d, title: item.title, start: item.start, end: item.end, teacher: first.teacher, location: first.location, candidates: item.candidates, manual: false });
  }
  return result;
}
export function allLessons(data: WebAppData, from: string, to: string) {
  return [...scheduleOccurrences(data.schedule, from, to), ...manualOccurrences(data.manualLessons, from, to)]
    .filter((x) => !x.cancelled).sort((a, b) => a.date.localeCompare(b.date) || a.start.localeCompare(b.start));
}

const dueDate = (task: StudyTask) => task.dueAt?.slice(0, 10);
const dateRange = (from: string, to: string) => {
  const out: string[] = []; for (let d = from; d <= to; d = addDays(d, 1)) out.push(d); return out;
};
export function taskOccurrences(tasks: StudyTask[], from: string, to: string): TaskOccurrence[] {
  const out: TaskOccurrence[] = [];
  for (const task of tasks) {
    const anchor = dueDate(task);
    if (!task.repeatRule || !anchor) {
      if (!anchor || (anchor >= from && anchor <= to)) out.push({ ...task, occurrenceKey: task.id, occurrenceDate: anchor, actualDueAt: task.dueAt, occurrenceCompletedAt: task.completedAt });
      continue;
    }
    const anchorWeekday = new Date(`${anchor}T00:00:00Z`).getUTCDay() || 7;
    for (const date of dateRange(anchor > from ? anchor : from, task.repeatRule.endsOn && task.repeatRule.endsOn < to ? task.repeatRule.endsOn : to)) {
      const weekday = new Date(`${date}T00:00:00Z`).getUTCDay() || 7;
      const fits = task.repeatRule.kind === "DAILY" || (task.repeatRule.kind === "WEEKLY" && weekday === anchorWeekday) || (task.repeatRule.kind === "CUSTOM_WEEKDAYS" && task.repeatRule.weekdays.includes(weekday));
      if (!fits) continue;
      const actualDueAt = `${date}T${task.dueAt!.slice(11, 16)}`;
      const key = `${task.id}@${actualDueAt}`;
      const state = task.instanceStates.find((x) => x.occurrenceKey === key);
      if (!state?.deleted) out.push({ ...task, occurrenceKey: key, occurrenceDate: date, actualDueAt, occurrenceCompletedAt: state?.completedAt });
    }
  }
  return out.sort((a, b) => (a.occurrenceCompletedAt ? 1 : 0) - (b.occurrenceCompletedAt ? 1 : 0) || (a.actualDueAt ?? "9999").localeCompare(b.actualDueAt ?? "9999") || priorityRank(a.priority) - priorityRank(b.priority));
}
export const priorityRank = (p: TaskPriority) => p === "URGENT" ? 0 : p === "IMPORTANT" ? 1 : 2;
export const taskTypeName: Record<TaskType, string> = { HOMEWORK: "作业", EXAM: "考试", REVIEW: "复习", OTHER: "其他" };
export const taskPriorityName: Record<TaskPriority, string> = { NORMAL: "普通", IMPORTANT: "重要", URGENT: "紧急" };

export function currentWeek(data: WebAppData, date = shanghaiDate()) {
  return data.schedule ? weekOf(data.schedule, date) : null;
}
export function semesterProgress(data: WebAppData, date = shanghaiDate()) {
  if (!data.schedule) return 0;
  return Math.max(0, Math.min(1, currentWeek(data, date)! / Math.max(1, lastWeek(data.schedule))));
}
export function normalizeData(input: Partial<WebAppData>): WebAppData {
  const base = defaultData();
  return {
    ...base, ...input, format: "campus-schedule-backup", version: 2,
    manualLessons: Array.isArray(input.manualLessons) ? input.manualLessons : [],
    tasks: Array.isArray(input.tasks) ? input.tasks : [], categories: Array.isArray(input.categories) && input.categories.length ? input.categories : base.categories,
    notes: Array.isArray(input.notes) ? input.notes : [], focusSessions: Array.isArray(input.focusSessions) ? input.focusSessions : [],
    achievements: Array.isArray(input.achievements) ? input.achievements : [], featuredAchievementIds: Array.isArray(input.featuredAchievementIds) ? input.featuredAchievementIds : [],
    profile: { ...base.profile, ...(input.profile ?? {}) }, settings: { ...base.settings, ...(input.settings ?? {}), noteStyle: { ...base.settings.noteStyle, ...(input.settings?.noteStyle ?? {}) }, launch: { ...base.settings.launch, ...(input.settings?.launch ?? {}) } },
  };
}

export const builtInAchievements = [
  { id: "first-light", name: "初光启页", description: "完成首次10分钟专注", icon: "☀", primary: "#16745A" },
  { id: "kapok-order", name: "木棉成序", description: "完成10个任务", icon: "✦", primary: "#C5524A" },
  { id: "clock-promise", name: "钟楼守约", description: "按时完成7个任务", icon: "◷", primary: "#1F5B48" },
  { id: "seven-stars", name: "七日星轨", description: "连续7天完成专注", icon: "✧", primary: "#31528D" },
  { id: "mountain-sea", name: "山海同频", description: "单周专注300分钟并完成5项任务", icon: "≈", primary: "#247C8B" },
] as const;

export function achievementProgress(data: WebAppData, id: string) {
  const completed = taskOccurrences(data.tasks, "2000-01-01", "2100-12-31").filter((x) => x.occurrenceCompletedAt).length;
  const validFocus = data.focusSessions.filter((x) => x.focusedSeconds >= 600);
  if (id === "first-light") return { value: Math.min(validFocus.length, 1), target: 1 };
  if (id === "kapok-order") return { value: Math.min(completed, 10), target: 10 };
  if (id === "clock-promise") return { value: Math.min(completed, 7), target: 7 };
  if (id === "seven-stars") return { value: Math.min(new Set(validFocus.map((x) => x.endedAt.slice(0, 10))).size, 7), target: 7 };
  const minutes = validFocus.reduce((n, x) => n + Math.floor(x.focusedSeconds / 60), 0);
  return { value: Math.min(Math.min(Math.floor(minutes / 60), completed), 5), target: 5 };
}
