package com.example.floatingmenu

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serviço em primeiro plano que mantém um menu flutuante sobre outros apps.
 *
 * Estados do menu:
 *  - recolhido: só o ícone redondo (arrastável; toque abre o painel)
 *  - expandido: painel com controles de teste (arraste pelo cabeçalho; "—" recolhe)
 *
 * O painel serve para validar layout e comportamento de toque de uma sobreposição:
 * caixas de seleção, interruptores, seletor de opções, controle deslizante e botões.
 */
class FloatingMenuService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var themedContext: Context

    private var iconView: View? = null
    private var panelView: View? = null
    private lateinit var iconParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    // Controle próprio de "está na tela": isAttachedToWindow só vira true após o 1º layout.
    private var iconShown = false
    private var panelShown = false
    private var ready = false

    private var logView: TextView? = null
    private val logLines = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // ---------------------------------------------------------------- ciclo de vida

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // O contexto do Service não tem tema de app; o wrapper garante estilo dos widgets.
        themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingMenu)

        startAsForeground()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        createIcon()
        createPanel()
        ready = true
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !ready) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotação: reposiciona as janelas visíveis dentro dos novos limites.
        iconView?.takeIf { iconShown }?.let { v -> v.post { clampAndUpdate(iconParams, v) } }
        panelView?.takeIf { panelShown }?.let { v ->
            v.post {
                fitPanelHeight()
                clampAndUpdate(panelParams, v)
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        removeIcon()
        removePanel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- notificação

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, FloatingMenuService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .addAction(0, getString(R.string.notif_stop), stopIntent)
            .build()

        // Android 14+ exige o tipo do serviço em primeiro plano (declarado no manifest).
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    // ---------------------------------------------------------------- ícone flutuante

    private fun createIcon() {
        val view = LayoutInflater.from(themedContext).inflate(R.layout.floating_icon, null)

        iconParams = newLayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        ).apply {
            x = dp(DEFAULT_X_DP)
            y = dp(DEFAULT_Y_DP)
        }

        view.setOnTouchListener(
            DraggableTouchListener(
                windowManager = windowManager,
                params = iconParams,
                windowView = view,
                screenSize = ::screenSize,
                onClick = ::expand,
            )
        )

        iconView = view
        windowManager.addView(view, iconParams)
        iconShown = true
    }

    // ---------------------------------------------------------------- painel expandido

    private fun createPanel() {
        val view = LayoutInflater.from(themedContext).inflate(R.layout.floating_panel, null)

        panelParams = newLayoutParams(
            dp(PANEL_WIDTHS_DP[1]),
            WindowManager.LayoutParams.WRAP_CONTENT,
        ).apply {
            x = dp(DEFAULT_X_DP)
            y = dp(DEFAULT_Y_DP)
        }

        val header = view.findViewById<View>(R.id.panel_header)
        val content = view.findViewById<View>(R.id.panel_content)
        logView = view.findViewById(R.id.tv_log)

        // Arraste pelo cabeçalho (o botão "—" continua recebendo o próprio clique).
        header.setOnTouchListener(
            DraggableTouchListener(
                windowManager = windowManager,
                params = panelParams,
                windowView = view,
                screenSize = ::screenSize,
            )
        )
        view.findViewById<View>(R.id.btn_collapse).setOnClickListener { collapse() }

        // Caixa de seleção: contorno de depuração do layout.
        view.findViewById<CheckBox>(R.id.cb_outline).setOnCheckedChangeListener { _, checked ->
            content.setBackgroundResource(if (checked) R.drawable.bg_debug_outline else 0)
            log("Contorno de layout: ${if (checked) "ligado" else "desligado"}")
        }

        // Botão de alternância: modo compacto esconde o log e muda a altura do painel.
        view.findViewById<Switch>(R.id.sw_compact).setOnCheckedChangeListener { _, checked ->
            logView?.visibility = if (checked) View.GONE else View.VISIBLE
            fitPanelHeight()
            log("Modo compacto: ${if (checked) "ligado" else "desligado"}")
        }

        // Seletor de opções (radio): largura do painel.
        view.findViewById<RadioGroup>(R.id.rg_size).setOnCheckedChangeListener { _, checkedId ->
            val index = when (checkedId) {
                R.id.rb_small -> 0
                R.id.rb_large -> 2
                else -> 1
            }
            panelParams.width = dp(PANEL_WIDTHS_DP[index])
            fitPanelHeight()
            panelView?.takeIf { panelShown }?.let { v -> v.post { clampAndUpdate(panelParams, v) } }
            log("Largura do painel: ${PANEL_WIDTHS_DP[index]}dp")
        }

        // Seletor de opções (spinner): cor do cabeçalho.
        val spinner = view.findViewById<Spinner>(R.id.sp_accent)
        spinner.adapter = ArrayAdapter(
            themedContext,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.accent_options),
        )
        var firstSelection = true // o Spinner dispara um evento ao ser configurado
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                header.setBackgroundColor(ACCENT_COLORS[position])
                if (firstSelection) firstSelection = false else log("Cor: ${parent?.getItemAtPosition(position)}")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // Controle deslizante: opacidade do painel (30% a 100%).
        view.findViewById<SeekBar>(R.id.sb_opacity).setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    view.alpha = (MIN_OPACITY_PERCENT + progress) / 100f
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit

                override fun onStopTrackingTouch(bar: SeekBar) {
                    log("Opacidade: ${MIN_OPACITY_PERCENT + bar.progress}%")
                }
            }
        )

        view.findViewById<Button>(R.id.btn_reset).setOnClickListener { resetPosition() }
        view.findViewById<Button>(R.id.btn_clear).setOnClickListener {
            logLines.clear()
            logView?.text = ""
        }

        panelView = view
        log("Serviço iniciado")
    }

    /** Troca o ícone pelo painel, na mesma posição. */
    private fun expand() {
        val panel = panelView ?: return
        if (panelShown) return

        panelParams.x = iconParams.x
        panelParams.y = iconParams.y
        fitPanelHeight()

        removeIcon()
        windowManager.addView(panel, panelParams)
        panelShown = true
        panel.post { clampAndUpdate(panelParams, panel) }
        log("Painel expandido")
    }

    /** Troca o painel pelo ícone, na mesma posição. */
    private fun collapse() {
        val icon = iconView ?: return
        if (!panelShown) return

        iconParams.x = panelParams.x
        iconParams.y = panelParams.y

        removePanel()
        windowManager.addView(icon, iconParams)
        iconShown = true
        icon.post { clampAndUpdate(iconParams, icon) }
        log("Painel recolhido")
    }

    private fun resetPosition() {
        listOf(iconParams to iconView, panelParams to panelView).forEach { (params, view) ->
            params.x = dp(DEFAULT_X_DP)
            params.y = dp(DEFAULT_Y_DP)
            if (view != null && (view === panelView && panelShown || view === iconView && iconShown)) {
                windowManager.updateViewLayout(view, params)
            }
        }
        log("Posição resetada")
    }

    /**
     * Se o painel for mais alto que 80% da tela, limita a altura (o conteúdo ganha rolagem);
     * caso contrário deixa a janela se ajustar ao conteúdo.
     */
    private fun fitPanelHeight() {
        val panel = panelView ?: return
        val (_, screenH) = screenSize()
        val maxHeight = (screenH * MAX_PANEL_HEIGHT_FRACTION).toInt()

        panel.measure(
            View.MeasureSpec.makeMeasureSpec(panelParams.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        panelParams.height =
            if (panel.measuredHeight > maxHeight) maxHeight else WindowManager.LayoutParams.WRAP_CONTENT

        if (panelShown) windowManager.updateViewLayout(panel, panelParams)
    }

    // ---------------------------------------------------------------- utilidades

    private fun removeIcon() {
        val view = iconView
        if (view != null && iconShown) {
            runCatching { windowManager.removeView(view) }
            iconShown = false
        }
    }

    private fun removePanel() {
        val view = panelView
        if (view != null && panelShown) {
            runCatching { windowManager.removeView(view) }
            panelShown = false
        }
    }

    private fun newLayoutParams(width: Int, height: Int): WindowManager.LayoutParams {
        @Suppress("DEPRECATION")
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            width,
            height,
            overlayType,
            // NOT_FOCUSABLE: o teclado e os botões de navegação continuam valendo para o app de baixo.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    /** Mantém a janela inteira dentro da tela e aplica a posição. */
    private fun clampAndUpdate(params: WindowManager.LayoutParams, view: View) {
        val (screenW, screenH) = screenSize()
        params.x = params.x.coerceIn(0, (screenW - view.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenH - view.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = resources.displayMetrics
            metrics.widthPixels to metrics.heightPixels
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Escreve no log do painel (mantém só as últimas linhas). */
    private fun log(message: String) {
        logLines.add("${timeFormat.format(Date())}  $message")
        while (logLines.size > MAX_LOG_LINES) logLines.removeAt(0)
        logView?.text = logLines.joinToString("\n")
    }

    companion object {
        const val ACTION_STOP = "com.example.floatingmenu.action.STOP"

        private const val CHANNEL_ID = "floating_menu"
        private const val NOTIF_ID = 1

        private const val DEFAULT_X_DP = 24
        private const val DEFAULT_Y_DP = 200
        private const val MAX_LOG_LINES = 5
        private const val MIN_OPACITY_PERCENT = 30
        private const val MAX_PANEL_HEIGHT_FRACTION = 0.8f

        private val PANEL_WIDTHS_DP = intArrayOf(240, 280, 320)
        private val ACCENT_COLORS = intArrayOf(
            0xFF3B82F6.toInt(), // azul
            0xFF16A34A.toInt(), // verde
            0xFFEA580C.toInt(), // laranja
        )

        /** Usado pela tela inicial para mostrar o estado do serviço. */
        @Volatile
        var isRunning = false
            private set
    }
}
