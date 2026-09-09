package cn.campus.schedule

import android.app.*
import android.content.*
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.campus.core.*
import kotlinx.coroutines.launch
import java.time.*

object FocusRuntime {
    const val TIMER_CHANNEL="focus_timer"
    const val COMPLETE_CHANNEL="focus_complete"
    const val NOTIFICATION=61
    const val COMPLETE_NOTIFICATION=62
    const val ACTION_ALARM="cn.campus.schedule.FOCUS_COMPLETE"
    const val ACTION_PAUSE="cn.campus.schedule.FOCUS_PAUSE"
    const val ACTION_RESUME="cn.campus.schedule.FOCUS_RESUME"
    const val ACTION_STOP="cn.campus.schedule.FOCUS_STOP"
    fun channels(context:Context)=context.getSystemService(NotificationManager::class.java).apply {
        createNotificationChannel(NotificationChannel(TIMER_CHANNEL,"专注计时",NotificationManager.IMPORTANCE_LOW).apply{description="显示正在进行的专注计时";setSound(null,null);enableVibration(false)})
        createNotificationChannel(NotificationChannel(COMPLETE_CHANNEL,"专注完成",NotificationManager.IMPORTANCE_HIGH).apply{description="专注倒计时结束提醒"})
    }
    private fun action(context:Context,action:String,code:Int)=PendingIntent.getBroadcast(context,code,Intent(context,FocusReceiver::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun alarm(context:Context)=action(context,ACTION_ALARM,64)
    fun buildOngoing(context:Context,active:ActiveFocusState):Notification {
        val now=Instant.now();val elapsed=FocusEngine.elapsedSeconds(active,now,SystemClock.elapsedRealtime());val remaining=active.plannedSeconds?.let{(it-elapsed).coerceAtLeast(0)}
        val open=PendingIntent.getActivity(context,NOTIFICATION,Intent(context,MainActivity::class.java).putExtra("openFocus",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=NotificationCompat.Builder(context,TIMER_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(active.title)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .addAction(0,if(active.status==FocusStatus.RUNNING)"暂停" else "继续",action(context,if(active.status==FocusStatus.RUNNING)ACTION_PAUSE else ACTION_RESUME,65))
            .addAction(0,"结束",action(context,ACTION_STOP,66))
        if(active.status==FocusStatus.PAUSED) builder.setContentText("已暂停 · ${formatSeconds(elapsed)}").setUsesChronometer(false)
        else if(remaining!=null) builder.setContentText("倒计时进行中").setWhen(System.currentTimeMillis()+remaining*1000).setUsesChronometer(true).setChronometerCountDown(true)
        else builder.setContentText("正计时进行中").setWhen(System.currentTimeMillis()-elapsed*1000).setUsesChronometer(true)
        return builder.build()
    }
    suspend fun refresh(context:Context) {
        channels(context)
        var data=context.scheduleApp.store.read();var active=data.activeFocus
        val elapsedRealtime=SystemClock.elapsedRealtime();val now=Instant.now()
        if(active!=null&&active.status==FocusStatus.RUNNING&&active.mode==FocusMode.STOPWATCH&&active.runStartedElapsedMs>elapsedRealtime) {
            val boot=now.minusMillis(elapsedRealtime);active=FocusEngine.pause(active,boot,0);context.scheduleApp.store.update{it.copy(activeFocus=active)}
        }
        val elapsed=active?.let{FocusEngine.elapsedSeconds(it,now,elapsedRealtime)}?:0L
        val remaining=active?.let{FocusEngine.remainingSeconds(it,now,elapsedRealtime)}
        if(active!=null&&active.status==FocusStatus.RUNNING&&(remaining==0L||(remaining==null&&elapsed>=FocusEngine.MAX_STOPWATCH_SECONDS))){complete(context,active,FocusStatus.COMPLETED);return}
        val manager=context.getSystemService(NotificationManager::class.java);val alarms=context.getSystemService(AlarmManager::class.java);alarms.cancel(alarm(context))
        if(active==null){manager.cancel(NOTIFICATION);FocusAudioService.stop(context);return}
        if(active.status==FocusStatus.RUNNING){
            val untilEnd=remaining?: (FocusEngine.MAX_STOPWATCH_SECONDS-elapsed).coerceAtLeast(0)
            ReminderScheduler.scheduleAlarm(alarms,context,now.plusSeconds(untilEnd),alarm(context))
        }
        if(active.ambientSound!=AmbientSound.NONE&&active.status==FocusStatus.RUNNING)FocusAudioService.start(context,active.ambientSound,active.volume)
        else {FocusAudioService.stop(context);NotificationManagerCompat.from(context).notify(NOTIFICATION,buildOngoing(context,active))}
        StudyWidget().updateAllSafe(context)
    }
    suspend fun complete(context:Context,active:ActiveFocusState,status:FocusStatus) {
        val now=Instant.now();val session=FocusEngine.finish(active,now,SystemClock.elapsedRealtime(),status)
        context.scheduleApp.store.update{state->AchievementEngine.evaluate(state.copy(activeFocus=null,focusSessions=(state.focusSessions+session).takeLast(5000))).first}
        if(active.mode!=FocusMode.BREAK)context.getSharedPreferences("focus",0).edit().putString("pendingCompletion",session.id).apply()
        context.getSystemService(AlarmManager::class.java).cancel(alarm(context));FocusAudioService.stop(context)
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        if(status==FocusStatus.COMPLETED){
            val openIntent=Intent(context,MainActivity::class.java).apply{if(active.mode!=FocusMode.BREAK)putExtra("focusCompletedId",session.id)}
            val open=PendingIntent.getActivity(context,COMPLETE_NOTIFICATION,openIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationManagerCompat.from(context).notify(COMPLETE_NOTIFICATION,NotificationCompat.Builder(context,COMPLETE_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(if(active.mode==FocusMode.BREAK)"休息结束" else "专注完成")
                .setContentText(if(active.mode==FocusMode.BREAK)"可以开始下一段学习了" else "${session.title} · ${session.focusedSeconds/60} 分钟").setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build())
        }
        ReminderScheduler.refresh(context)
    }
    fun formatSeconds(value:Long)=if(value>=3600)String.format("%02d:%02d:%02d",value/3600,(value%3600)/60,value%60)else String.format("%02d:%02d",value/60,value%60)
}

class FocusReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){val pending=goAsync();context.scheduleApp.scope.launch{try{
        val active=context.scheduleApp.store.read().activeFocus?:return@launch;val now=Instant.now();val elapsed=SystemClock.elapsedRealtime()
        when(intent.action){
            FocusRuntime.ACTION_PAUSE->context.scheduleApp.store.update{it.copy(activeFocus=FocusEngine.pause(active,now,elapsed))}
            FocusRuntime.ACTION_RESUME->context.scheduleApp.store.update{it.copy(activeFocus=FocusEngine.resume(active,now,elapsed))}
            FocusRuntime.ACTION_STOP->FocusRuntime.complete(context,active,FocusStatus.STOPPED)
            FocusRuntime.ACTION_ALARM->FocusRuntime.complete(context,active,FocusStatus.COMPLETED)
        }
        if(intent.action!=FocusRuntime.ACTION_STOP&&intent.action!=FocusRuntime.ACTION_ALARM)FocusRuntime.refresh(context)
    }finally{pending.finish()}}}
}

class FocusAudioService:Service(){
    private var player:MediaPlayer?=null;private var request:AudioFocusRequest?=null;private var sound:AmbientSound=AmbientSound.NONE
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        val next=runCatching{AmbientSound.valueOf(intent?.getStringExtra("sound")?:"NONE")}.getOrDefault(AmbientSound.NONE);val volume=intent?.getFloatExtra("volume",.35f)?.coerceIn(0f,1f)?:.35f
        val active=runCatching{kotlinx.coroutines.runBlocking{applicationContext.scheduleApp.store.read().activeFocus}}.getOrNull()?:run{stopSelf();return START_NOT_STICKY}
        try{startForeground(FocusRuntime.NOTIFICATION,FocusRuntime.buildOngoing(this,active))}catch(_:Exception){stopSelf();return START_NOT_STICKY}
        if(next==sound&&player?.isPlaying==true){player?.setVolume(volume,volume);return START_STICKY}
        release();sound=next
        if(next==AmbientSound.NONE){stopSelf();return START_NOT_STICKY}
        val manager=getSystemService(AudioManager::class.java);val attrs=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setOnAudioFocusChangeListener{change->when(change){AudioManager.AUDIOFOCUS_LOSS,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT->player?.pause();AudioManager.AUDIOFOCUS_GAIN->runCatching{player?.start()}}}.build()
        if(manager.requestAudioFocus(request!!)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){stopSelf();return START_NOT_STICKY}
        val res=when(next){AmbientSound.RAIN->R.raw.focus_rain;AmbientSound.WAVES->R.raw.focus_waves;AmbientSound.LIBRARY->R.raw.focus_library;else->0}
        player=MediaPlayer.create(this,res)?.apply{isLooping=true;setVolume(volume,volume);start()}
        return START_STICKY
    }
    private fun release(){runCatching{player?.release()};player=null;request?.let{getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it)};request=null;sound=AmbientSound.NONE}
    override fun onDestroy(){release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
    companion object{
        fun start(context:Context,sound:AmbientSound,volume:Float)=ContextCompat.startForegroundService(context,Intent(context,FocusAudioService::class.java).putExtra("sound",sound.name).putExtra("volume",volume))
        fun stop(context:Context)=context.stopService(Intent(context,FocusAudioService::class.java))
    }
}
