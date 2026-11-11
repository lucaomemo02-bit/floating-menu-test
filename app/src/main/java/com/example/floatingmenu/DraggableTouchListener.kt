package com.example.floatingmenu

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.hypot

/**
 * Torna uma janela do WindowManager arrastável e diferencia "arrastar" de "tocar".
 *
 * Use em qualquer view que seja a raiz (ou a alça de arraste) de uma janela flutuante.
 * Os parâmetros [params] precisam usar Gravity.TOP | Gravity.START, assim x/y são
 * deslocamentos a partir do canto superior esquerdo da tela.
 *
 * @param windowView view raiz adicionada ao WindowManager (a que será movida)
 * @param screenSize fornece (largura, altura) atuais da tela, para não sair dos limites
 * @param onClick chamado quando o dedo sobe sem ter passado do "touch slop"
 * @param onMoved chamado a cada atualização de posição durante o arraste
 */
class DraggableTouchListener(
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val windowView: View,
    private val screenSize: () -> Pair<Int, Int>,
    private val onClick: (() -> Unit)? = null,
    private val onMoved: (() -> Unit)? = null,
) : View.OnTouchListener {

    private var startParamX = 0
    private var startParamY = 0
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var dragging = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startParamX = params.x
                startParamY = params.y
                startTouchX = event.rawX
                startTouchY = event.rawY
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startTouchX
                val dy = event.rawY - startTouchY
                val slop = ViewConfiguration.get(v.context).scaledTouchSlop

                // Só vira arraste depois de passar do limite, evitando "tremida" no clique.
                if (!dragging && hypot(dx, dy) > slop) dragging = true

                if (dragging) {
                    val (screenW, screenH) = screenSize()
                    val maxX = (screenW - windowView.width).coerceAtLeast(0)
                    val maxY = (screenH - windowView.height).coerceAtLeast(0)
                    params.x = (startParamX + dx.toInt()).coerceIn(0, maxX)
                    params.y = (startParamY + dy.toInt()).coerceIn(0, maxY)
                    windowManager.updateViewLayout(windowView, params)
                    onMoved?.invoke()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    v.performClick() // acessibilidade
                    onClick?.invoke()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return false
    }
}
