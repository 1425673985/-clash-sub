package com.ddz.game.util

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object AnimationHelper {

    fun animateCardPlay(view: View, onEnd: () -> Unit = {}) {
        view.animate()
            .scaleX(1.15f).scaleY(1.15f)
            .setDuration(120)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(100)
                    .withEndAction(onEnd)
                    .start()
            }.start()
    }

    fun animateBomb(rootView: View) {
        ObjectAnimator.ofFloat(rootView, "translationX",
            0f, -20f, 20f, -14f, 14f, -8f, 8f, 0f
        ).apply {
            duration = 400
            start()
        }
    }

    fun animateFlip(view: View, onMidpoint: () -> Unit) {
        view.animate().scaleX(0f).setDuration(150).withEndAction {
            onMidpoint()
            view.animate().scaleX(1f).setDuration(150).start()
        }.start()
    }

    fun animateFadeIn(view: View, delay: Long = 0L) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).setStartDelay(delay).setDuration(300).start()
    }

    fun animatePopIn(view: View) {
        view.scaleX = 0f; view.scaleY = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    fun animateShake(view: View) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, -10f, 10f, -8f, 8f, 0f).apply {
            duration = 300
            start()
        }
    }
}
