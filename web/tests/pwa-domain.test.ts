import { describe, expect, test } from "vitest";
import { defaultData, manualOccurrences, taskOccurrences, type ManualLesson, type StudyTask } from "../src/domain";
import { createCalendar } from "../src/ics";
import { imageDraftToResult, parseImagePages, type ImageOcrPage } from "../src/image-import";

describe("PWA expanded domain", () => {
  test("weekly manual lessons expand with stable keys", () => {
    const item: ManualLesson = { id:"lab",date:"2026-09-07",title:"芯片实验",start:"08:00",end:"09:40",teacher:"",location:"实验楼",color:"#176B52",repeatCount:30,revisions:[],instanceEdits:[{index:2,cancelled:true}] };
    const result=manualOccurrences([item]);
    expect(result).toHaveLength(30);
    expect(result[0].key).toBe("manual@lab@0");
    expect(result[29].date).toBe("2027-03-29");
    expect(result[2].cancelled).toBe(true);
  });
  test("custom weekday task creates independent occurrences", () => {
    const task: StudyTask={id:"task",title:"复习",type:"REVIEW",courseTitle:"",dueAt:"2026-09-07T20:00",priority:"NORMAL",note:"",createdAt:"2026-09-01T00:00:00Z",subtasks:[],instanceStates:[],repeatRule:{kind:"CUSTOM_WEEKDAYS",weekdays:[1,3],endsOn:"2026-09-13"}};
    expect(taskOccurrences([task],"2026-09-07","2026-09-13").map(x=>x.occurrenceDate)).toEqual(["2026-09-07","2026-09-09"]);
  });
  test("ICS uses Asia/Shanghai conversion and alarms", () => {
    const data=defaultData();data.manualLessons=[{id:"one",date:"2026-09-21",title:"高等数学",start:"08:00",end:"09:40",teacher:"杨老师",location:"逸201",color:"#176B52",repeatCount:1,revisions:[],instanceEdits:[]}];
    const value=createCalendar(data,10);
    expect(value).toContain("SUMMARY:高等数学");
    expect(value).toContain("DTSTART:20260921T000000Z");
    expect(value).toContain("TRIGGER:-PT10M");
  });
  test("image table tokens restore a preview", () => {
    const tokens:any[]=[];for(let i=0;i<7;i++)tokens.push({pageIndex:0,text:`星期${"一二三四五六日"[i]}`,left:100+i*100,top:20,right:180+i*100,bottom:50,confidence:.99});
    tokens.push({pageIndex:0,text:"第1节 08:00-08:45",left:0,top:100,right:90,bottom:130,confidence:.99});
    tokens.push({pageIndex:0,text:"第2节 08:55-09:40",left:0,top:160,right:90,bottom:190,confidence:.99});
    tokens.push({pageIndex:0,text:"1-17周每周/本(专必)高等数学/杨老师/逸201",left:105,top:90,right:175,bottom:200,confidence:.95});
    const page:ImageOcrPage={index:0,width:900,height:800,tokens};
    const draft=parseImagePages([page],"2026-09-07","FULL_TERM_TABLE");
    const result=imageDraftToResult(draft);
    expect(result.schedule.rules[0].title).toBe("高等数学");
    expect(result.schedule.rules[0].variants[0].weeks).toHaveLength(17);
  });
});
