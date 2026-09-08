package cn.campus.schedule

import android.app.*
import android.content.*
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** User-enabled course alarm, bounded to one minute. Never launched by boot recovery. */
class AlarmPlaybackService : Service() {
    private var player: MediaPlayer? = null
    private val handler=Handler(Looper.getMainLooper())
    private var focus: AudioFocusRequest? = null
    private val timeout=Runnable { stopSelf() }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent==null) {stopSelf();return START_NOT_STICKY}
        isTest=intent.getBooleanExtra("test",false)
        val channel="alarm_playback"
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"闹铃播放与停止",NotificationManager.IMPORTANCE_HIGH).apply {
            description="显示正在响铃的课程和停止按钮";setSound(null,null);enableVibration(false)
        })
        val stop=PendingIntent.getBroadcast(this,81,Intent(this,ReminderReceiver::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,81,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(intent.getStringExtra("title") ?: "课前闹铃")
            .setContentText("正在响铃，最多 1 分钟 · 点击停止闹铃")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open).setOngoing(true).addAction(0,"停止闹铃",stop).build()
        try { startForeground(NOTIFICATION,notification) } catch(_:Exception) {stopSelf();return START_NOT_STICKY}
        handler.removeCallbacks(timeout);handler.postDelayed(timeout,60_000)
        releaseAudio()
        try {
            val attrs=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val manager=getSystemService(AudioManager::class.java)
            val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { if(it==AudioManager.AUDIOFOCUS_LOSS) stopSelf() }.build()
            focus=request
            if(manager.requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {stopSelf();return START_NOT_STICKY}
            val media=MediaPlayer();player=media
            media.setAudioAttributes(attrs)
            media.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK)
            media.setDataSource(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            media.isLooping=true
            media.setOnErrorListener { _,_,_ -> stopSelf();true }
            media.prepare();media.start();isPlaying=true
        } catch(_:Exception) {
            ReminderScheduler.notify(this,7,"闹铃未能播放","请检查系统闹钟铃声、闹钟音量和后台限制。课前通知仍可查看。")
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun releaseAudio() {
        isPlaying=false
        runCatching {player?.release()};player=null
        focus?.let {getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it)};focus=null
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null);releaseAudio();isTest=false
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()
    }
    companion object {
        const val STOP="cn.campus.schedule.STOP_ALARM"
        const val NOTIFICATION=9
        @Volatile var isPlaying=false; private set
        @Volatile var isTest=false; private set
        fun start(context: Context,title: String,test: Boolean=false): Boolean = runCatching {
            ContextCompat.startForegroundService(context,Intent(context,AlarmPlaybackService::class.java).putExtra("title",title).putExtra("test",test));true
        }.getOrDefault(false)
        fun stop(context: Context) {context.stopService(Intent(context,AlarmPlaybackService::class.java))}
    }
}
