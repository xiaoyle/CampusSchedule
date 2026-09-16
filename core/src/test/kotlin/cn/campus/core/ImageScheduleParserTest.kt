package cn.campus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageScheduleParserTest {
    @Test fun parsesFullTermTableFromOcrBlocks(){
        val tokens=mutableListOf<ImageOcrToken>()
        (1..7).forEach{day->tokens+=token("星期"+"一二三四五六日"[day-1],100f*day,80f,day)}
        (1..4).forEach{period->tokens+=token("第${period}节",25f,150f+period*100,20+period)}
        tokens+=ImageOcrToken(0,"1-17每周/本(专必)高等数学/杨老师/逸201",75f,230f,125f,320f,.94f)
        val draft=ImageScheduleParser.parse(listOf(ImageOcrPage(0,800,800,tokens)),"2026-09-07")
        assertEquals(ImageImportKind.FULL_TERM_TABLE,draft.kind)
        assertEquals(1,draft.rows.size)
        assertEquals(1,draft.rows.single().weekday)
        assertEquals("高等数学",draft.rows.single().title)
        assertEquals(17,draft.capturedWeeks.size)
        assertEquals(1,draft.toImportResult().schedule.rules.size)
    }

    @Test fun parsesSeveralMobileWeeksWithoutInventingMissingWeeks(){
        fun page(index:Int,week:Int)=ImageOcrPage(index,800,1000,buildList{
            add(token("2026学年度第一学期",400f,30f,index))
            add(token("第${week}周",400f,65f,index))
            (1..7).forEach{day->add(token((7+day).toString(),100f*day,120f,index))}
            listOf("08:00","08:55","10:10","11:05").forEachIndexed{i,time->add(token(time,25f,230f+i*100,index))}
            add(ImageOcrToken(index,"高等数学/逸201",75f,215f,125f,315f,.91f))
        })
        val draft=ImageScheduleParser.parse(listOf(page(0,1),page(1,3)),"2026-09-07",ImageImportKind.WEEKLY_MOBILE)
        assertEquals(listOf(1,3),draft.capturedWeeks)
        val rule=draft.toImportResult().schedule.rules.single()
        assertEquals(listOf(1,3),rule.variants.single().weeks)
    }

    @Test fun rejectsImageWithoutScheduleStructure(){
        val page=ImageOcrPage(0,1000,1000,List(10){token("普通文字$it",100f,100f+it*30,0)})
        assertFailsWith<IllegalArgumentException>{ImageScheduleParser.parse(listOf(page),"2026-09-07")}
    }

    private fun token(text:String,x:Float,y:Float,page:Int)=ImageOcrToken(page,text,x-20,y-10,x+20,y+10,.96f)
}
