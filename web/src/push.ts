import { allLessons, taskOccurrences, type WebAppData } from "./domain";
import { addDays } from "./model";

interface PushReminder { id: string; triggerAt: string; ciphertext: string; iv: string }

const bytesToBase64 = (value: ArrayBuffer | Uint8Array) => {
  const bytes = value instanceof Uint8Array ? value : new Uint8Array(value); let text = "";
  for (const byte of bytes) text += String.fromCharCode(byte); return btoa(text);
};
const base64ToBytes = (value: string) => Uint8Array.from(atob(value.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));
const db = () => new Promise<IDBDatabase>((resolve, reject) => { const request=indexedDB.open("campus-schedule-web",2);request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error); });
async function metaGet<T>(key:string):Promise<T|undefined>{const database=await db();return new Promise((resolve,reject)=>{const tx=database.transaction("meta","readonly"),req=tx.objectStore("meta").get(key);req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error)})}
async function metaPut(key:string,value:unknown){const database=await db();return new Promise<void>((resolve,reject)=>{const tx=database.transaction("meta","readwrite");tx.objectStore("meta").put(value,key);tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error)})}
async function localKey(){let raw=await metaGet<string>("push-secret");if(!raw){const generated=crypto.getRandomValues(new Uint8Array(32));raw=bytesToBase64(generated);await metaPut("push-secret",raw)}return crypto.subtle.importKey("raw",base64ToBytes(raw),"AES-GCM",false,["encrypt"])}
async function encrypt(value:unknown){const key=await localKey(),iv=crypto.getRandomValues(new Uint8Array(12)),plain=new TextEncoder().encode(JSON.stringify(value)),cipher=await crypto.subtle.encrypt({name:"AES-GCM",iv},key,plain);return{ciphertext:bytesToBase64(cipher),iv:bytesToBase64(iv)}}
function vapidKey(value:string){return base64ToBytes(value.padEnd(value.length+(4-value.length%4)%4,"="))}

export async function enablePush(data:WebAppData){
  if(!data.settings.pushEndpoint||!data.settings.pushPublicKey)throw new Error("请先填写推送服务地址和 VAPID 公钥");
  if(!matchMedia("(display-mode: standalone)").matches&&!(navigator as any).standalone)throw new Error("请先添加到主屏幕，再从图标打开并开启通知");
  if(await Notification.requestPermission()!=="granted")throw new Error("通知权限未开启，可在 iPhone 设置中调整");
  const registration=await navigator.serviceWorker.ready;
  const subscription=await registration.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:vapidKey(data.settings.pushPublicKey)});
  let token=await metaGet<string>("push-device");if(!token){token=crypto.randomUUID();await metaPut("push-device",token)}
  const reminders=await buildReminders(data);
  const response=await fetch(`${data.settings.pushEndpoint.replace(/\/$/,"")}/v1/push/subscriptions`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({deviceToken:token,subscription:subscription.toJSON(),reminders})});
  if(!response.ok)throw new Error(`推送服务返回 ${response.status}，请检查服务器配置`);
  return reminders.length;
}

export async function syncPushIfEnabled(data:WebAppData){
  if(!data.settings.pushEndpoint||Notification.permission!=="granted"||!("serviceWorker" in navigator))return;
  const registration=await navigator.serviceWorker.ready,subscription=await registration.pushManager.getSubscription();if(!subscription)return;
  const token=await metaGet<string>("push-device");if(!token)return;
  const reminders=await buildReminders(data),response=await fetch(`${data.settings.pushEndpoint.replace(/\/$/,"")}/v1/push/subscriptions`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({deviceToken:token,subscription:subscription.toJSON(),reminders})});
  if(!response.ok)throw new Error(`提醒同步失败（${response.status}）`);
}

export async function disablePush(data:WebAppData){const token=await metaGet<string>("push-device");const registration=await navigator.serviceWorker.ready;await(await registration.pushManager.getSubscription())?.unsubscribe();if(token&&data.settings.pushEndpoint)await fetch(`${data.settings.pushEndpoint.replace(/\/$/,"")}/v1/push/subscriptions/${encodeURIComponent(token)}`,{method:"DELETE"}).catch(()=>{});}

export async function buildReminders(data:WebAppData):Promise<PushReminder[]>{
  const from=new Date().toISOString().slice(0,10),to=addDays(from,90),drafts:{id:string;triggerAt:string;content:unknown}[]=[];
  for(const lesson of allLessons(data,from,to)){const start=Date.parse(`${lesson.date}T${lesson.start}:00+08:00`),trigger=start-10*60_000;if(trigger>Date.now())drafts.push({id:`lesson-${lesson.key}`,triggerAt:new Date(trigger).toISOString(),content:{title:`${lesson.start} ${lesson.title}`,body:lesson.location||"地点待定",url:`#/today?from-notification=1&lesson=${encodeURIComponent(lesson.key)}`}})}
  for(const task of taskOccurrences(data.tasks,from,to)){if(!task.actualDueAt||task.occurrenceCompletedAt||task.remindBeforeMinutes===undefined)continue;const trigger=Date.parse(`${task.actualDueAt}:00+08:00`)-task.remindBeforeMinutes*60_000;if(trigger>Date.now())drafts.push({id:`task-${task.occurrenceKey}`,triggerAt:new Date(trigger).toISOString(),content:{title:`待办 · ${task.title}`,body:task.courseTitle||"点击查看详情",url:`#/task/edit/${encodeURIComponent(task.id)}?from-notification=1`}})}
  return Promise.all(drafts.slice(0,500).map(async x=>({...x,...await encrypt(x.content),content:undefined} as PushReminder)));
}
