package dev.badalab.yeonfeel.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * 카드형 설정 화면 빌더 (라이트 스킴):
 * 연회색 배경 + 흰색 라운드 카드 그룹 + 큰 타이틀 헤더 + 파란 액센트.
 */
class SettingComponents(private val activity: Activity) {

    private val density = activity.resources.displayMetrics.density

    init {
        // 시스템 액션바 없이 커스텀 헤더만 쓰므로 상태 바를 배경색에 맞춘다.
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = BG
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    fun dp(v: Int): Int = (v * density).toInt()

    /**
     * 실감 나는 누름 효과: 손이 닿는 즉시 행 전체가 빈틈없이 어두워지고
     * (퍼지는 리플 없이), 내용(자식 뷰)만 살짝 들어갔다가 손을 떼면 돌아온다.
     * 클릭 리스너와 함께 동작한다.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun addPressEffect(view: View, cornerDp: Int = 0, visualTarget: View = view) {
        // 시스템 pressed 상태는 스크롤 판정 때문에 지연되므로 터치 즉시 직접 씌운다.
        val overlay = GradientDrawable().apply {
            setColor(0x26000000)
            if (cornerDp > 0) cornerRadius = dp(cornerDp).toFloat()
        }
        var fadeOut: android.animation.ValueAnimator? = null
        // 행 자체를 줄이면 어두운 영역도 함께 줄어 가장자리가 비어 보이므로 내용만 줄인다.
        fun contents(): List<View> = if (visualTarget is android.view.ViewGroup && visualTarget.childCount > 0) {
            (0 until visualTarget.childCount).map(visualTarget::getChildAt)
        } else {
            emptyList()
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    fadeOut?.cancel()
                    overlay.alpha = 255
                    visualTarget.foreground = overlay
                    contents().forEach { it.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).start() }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    fadeOut = android.animation.ValueAnimator.ofInt(255, 0).apply {
                        duration = 140
                        addUpdateListener {
                            overlay.alpha = it.animatedValue as Int
                            visualTarget.invalidate()
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                visualTarget.foreground = null
                            }
                        })
                        start()
                    }
                    contents().forEach { it.animate().scaleX(1f).scaleY(1f).setDuration(140).start() }
                }
            }
            false
        }
    }

    private val column = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, dp(12), dp(24))
        // 섹션이 조건부로 나타나고 사라질 때 주변 콘텐츠는 밀리기만 하고 새 섹션만 페이드된다.
        layoutTransition = android.animation.LayoutTransition()
    }

    fun root(): View = ScrollView(activity).apply {
        fitsSystemWindows = true
        setBackgroundColor(BG)
        addView(column)
    }

    /**
     * 화면을 표시한다. 재구성 시에는 기존 스크롤 루트를 유지한 채 내용만 바꿔
     * 헤더 위치·스크롤 상태가 보존되고, 새 내용이 위에서 펼쳐지듯 나타난다.
     */
    fun show(custom: View? = null) {
        if (custom != null) {
            activity.setContentView(custom)
            return
        }
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val existing = content.findViewWithTag<ScrollView>(ROOT_TAG)
        if (existing == null) {
            activity.setContentView(root().apply { tag = ROOT_TAG })
            return
        }
        existing.removeAllViews()
        existing.addView(column)
        column.alpha = 0f
        column.translationY = dp(-8).toFloat()
        column.animate().alpha(1f).translationY(0f).setDuration(160)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    fun header(
        title: String,
        showBack: Boolean = true,
        actionIcon: Int? = null,
        onAction: (() -> Unit)? = null,
    ) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(28), dp(8), dp(28))
        }
        if (showBack) {
            row.addView(TextView(activity).apply {
                text = "‹"
                textSize = 32f
                setTextColor(TEXT)
                setPadding(dp(8), 0, dp(20), dp(6))
                setOnClickListener { activity.finish() }
            })
        }
        row.addView(
            TextView(activity).apply {
                text = title
                textSize = 26f
                setTextColor(TEXT)
                setTypeface(null, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        actionIcon?.let { icon ->
            row.addView(
                android.widget.ImageView(activity).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(TEXT)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setOnClickListener { onAction?.invoke() }
                },
                LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) },
            )
        }
        column.addView(row)
    }

    /** 카드 밖 소제목. [actionIcon]을 주면 제목 옆에 작은 액션 버튼이 붙는다 (예: 초기화). */
    fun caption(text: String, actionIcon: Int? = null, onAction: (() -> Unit)? = null): View {
        val label = TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(SUB_TEXT)
        }
        if (actionIcon == null) {
            label.setPadding(dp(20), dp(4), dp(20), dp(10))
            column.addView(label)
            return label
        }
        val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(4), dp(20), dp(10))
                label.layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(label)
                addView(
                    android.widget.ImageView(activity).apply {
                        setImageResource(actionIcon)
                        imageTintList = ColorStateList.valueOf(SUB_TEXT)
                        setPadding(dp(4), dp(4), dp(4), dp(4))
                        layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                        setOnClickListener { onAction?.invoke() }
                        // 아이콘 크기에 맞는 원형 하이라이트
                        addPressEffect(this, cornerDp = 12)
                    },
                )
            }
        column.addView(row)
        return row
    }

    fun card(vararg rows: View): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD)
                cornerRadius = dp(24).toFloat()
            }
            // 행 리플이 카드 둥근 모서리 밖으로 번지지 않게 한다.
            clipToOutline = true
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                card.addView(View(activity).apply {
                    setBackgroundColor(DIVIDER)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1,
                    ).apply {
                        marginStart = dp(20)
                        marginEnd = dp(20)
                    }
                })
            }
            card.addView(row)
        }
        column.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(16) },
        )
        return card
    }

    fun textRow(label: String, subLabel: String? = null, onClick: (() -> Unit)? = null): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        row.addView(TextView(activity).apply {
            text = label
            textSize = 17f
            setTextColor(TEXT)
        })
        subLabel?.let {
            row.addView(TextView(activity).apply {
                text = it
                textSize = 13f
                setTextColor(SUB_TEXT)
                setPadding(0, dp(4), 0, 0)
            })
        }
        onClick?.let { handler ->
            row.setOnClickListener { handler() }
            addPressEffect(row)
        }
        return row
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun switchRow(label: String, checked: Boolean, onChange: (Boolean, Switch) -> Unit): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        row.addView(TextView(activity).apply {
            text = label
            textSize = 17f
            setTextColor(TEXT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val switch = Switch(activity).apply {
            isChecked = checked
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ACCENT, 0xFFC6CAD2.toInt()),
            )
            setOnCheckedChangeListener { view, isChecked -> onChange(isChecked, view as Switch) }
        }
        row.addView(switch)
        row.setOnClickListener { switch.toggle() }
        addPressEffect(row)
        return row
    }

    /**
     * 텍스트 영역을 누르면 하위 화면으로 이동하고, 오른쪽 스위치로 켜고 끄는 행.
     * 언어 목록처럼 이동과 토글이 함께 필요한 곳에 쓴다.
     */
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun switchNavRow(
        label: String,
        subLabel: String?,
        checked: Boolean,
        onToggle: (Boolean, Switch) -> Unit,
        onOpen: () -> Unit,
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        val textColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onOpen() }
        }
        textColumn.addView(TextView(activity).apply {
            text = label
            textSize = 17f
            setTextColor(TEXT)
        })
        subLabel?.let {
            textColumn.addView(TextView(activity).apply {
                text = it
                textSize = 13f
                setTextColor(ACCENT)
                setPadding(0, dp(2), 0, 0)
            })
        }
        // 텍스트 영역을 누르면 행 전체가 어두워지게 한다 (스위치는 자체 피드백 유지).
        addPressEffect(textColumn, visualTarget = row)
        row.addView(textColumn)
        // 하위 화면 이동 + 토글이 공존하는 행은 토글 왼쪽에 세로 디바이더를 둔다.
        row.addView(View(activity).apply {
            setBackgroundColor(DIVIDER_STRONG)
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(26)).apply {
                marginStart = dp(8)
                marginEnd = dp(14)
            }
        })
        row.addView(Switch(activity).apply {
            isChecked = checked
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ACCENT, 0xFFC6CAD2.toInt()),
            )
            setOnCheckedChangeListener { view, isChecked -> onToggle(isChecked, view as Switch) }
        })
        return row
    }

    /**
     * 라벨 + 슬라이더 행. 값이 바뀔 때마다 [onChange]가 호출되고,
     * [valueFormatter]를 주면 현재 값이 우측에 표시된다 (예: "300ms").
     * 트랙은 라벨과 같은 지점(카드 안 20dp)에서 시작·종료하고,
     * 썸(원)은 끝까지 가도 잘리지 않도록 여백을 썸 반지름으로 확보한다.
     */
    fun sliderRow(
        label: String,
        max: Int,
        initial: Int,
        min: Int = 0,
        valueFormatter: ((Int) -> String)? = null,
        onChange: (Int) -> Unit,
    ): SliderRowView {
        val thumbRadius = dp(11)
        val row = SliderRowView(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20) - thumbRadius, dp(14), dp(20) - thumbRadius, dp(14))
        }
        val valueView = valueFormatter?.let { formatter ->
            TextView(activity).apply {
                text = formatter(initial)
                textSize = 14f
                setTextColor(ACCENT)
            }
        }
        row.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(thumbRadius, 0, thumbRadius, 0)
                addView(TextView(activity).apply {
                    text = label
                    textSize = 17f
                    setTextColor(TEXT)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                valueView?.let { addView(it) }
            },
        )
        // SeekBar.min은 API 26+라서 오프셋으로 최소값을 흉내 낸다 (구형 기기 호환).
        val track = TrackDrawable(baseThickness = dp(4).toFloat())
        var thicknessAnimator: android.animation.ValueAnimator? = null
        fun animateThickness(to: Float) {
            thicknessAnimator?.cancel()
            thicknessAnimator = android.animation.ValueAnimator.ofFloat(track.thickness, to).apply {
                duration = 160
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { track.thickness = it.animatedValue as Float }
                start()
            }
        }
        row.addView(android.widget.SeekBar(activity).apply {
            row.applyValue = { value -> progress = value - min }
            this.max = max - min
            progress = initial - min
            progressDrawable = track
            thumb = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(ACCENT)
                setSize(thumbRadius * 2, thumbRadius * 2)
            }
            thumbOffset = 0
            // 트랙 시작점이 라벨과 정렬되도록 패딩 = 썸 반지름
            setPadding(thumbRadius, dp(10), thumbRadius, dp(4))
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
                    val actual = value + min
                    valueView?.text = valueFormatter?.invoke(actual)
                    if (fromUser) onChange(actual)
                }

                override fun onStartTrackingTouch(bar: android.widget.SeekBar?) =
                    animateThickness(dp(7).toFloat())

                override fun onStopTrackingTouch(bar: android.widget.SeekBar?) =
                    animateThickness(dp(4).toFloat())
            })
        })
        return row
    }

    /** 슬라이더 행 뷰: [setValue]로 화면 재구성 없이 값만 갱신할 수 있다 (onChange는 호출 안 됨). */
    class SliderRowView(context: android.content.Context) : LinearLayout(context) {
        internal var applyValue: ((Int) -> Unit)? = null
        fun setValue(value: Int) {
            applyValue?.invoke(value)
        }
    }

    /**
     * 슬라이더 트랙: 레벨(0~10000) 기반으로 진행선을 그리는 커스텀 드로어블.
     * [thickness]를 바꾸면 즉시 다시 그려져 누름 애니메이션에 쓴다.
     */
    private class TrackDrawable(baseThickness: Float) : android.graphics.drawable.Drawable() {
        private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC6CAD2.toInt()
        }
        private val fgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
        }

        var thickness: Float = baseThickness
            set(value) {
                field = value
                invalidateSelf()
            }

        override fun draw(canvas: android.graphics.Canvas) {
            val b = bounds
            val cy = b.exactCenterY()
            val r = thickness / 2f
            canvas.drawRoundRect(
                android.graphics.RectF(b.left.toFloat(), cy - r, b.right.toFloat(), cy + r),
                r, r, bgPaint,
            )
            val ratio = level / 10000f
            if (ratio > 0f) {
                canvas.drawRoundRect(
                    android.graphics.RectF(b.left.toFloat(), cy - r, b.left + b.width() * ratio, cy + r),
                    r, r, fgPaint,
                )
            }
        }

        override fun onLevelChange(level: Int): Boolean {
            invalidateSelf()
            return true
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    /**
     * 화면 하단에 붙는 확인 다이얼로그.
     * [onConfirm]은 확인 버튼을 눌렀을 때만 호출된다.
     */
    fun confirmBottom(
        title: String,
        message: String,
        confirmLabel: String,
        cancelLabel: String,
        onConfirm: () -> Unit,
    ) {
        val dialog = android.app.Dialog(activity)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD)
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(24), dp(22), dp(24), dp(12))
        }
        content.addView(TextView(activity).apply {
            text = title
            textSize = 18f
            setTextColor(TEXT)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(activity).apply {
            text = message
            textSize = 14f
            setTextColor(SUB_TEXT)
            setPadding(0, dp(8), 0, dp(14))
        })

        fun dialogButton(label: String, color: Int, onClick: () -> Unit) = TextView(activity).apply {
            text = label
            textSize = 15f
            setTextColor(color)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setOnClickListener { onClick() }
            addPressEffect(this, cornerDp = 18)
        }
        // 버튼은 전체 폭을 반씩 나눠 각자 중앙 정렬하고, 사이에 세로 디바이더를 둔다.
        content.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    dialogButton(cancelLabel, SUB_TEXT) { dialog.dismiss() },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    View(activity).apply { setBackgroundColor(DIVIDER_STRONG) },
                    LinearLayout.LayoutParams(dp(1), dp(20)),
                )
                addView(
                    dialogButton(confirmLabel, ACCENT) {
                        dialog.dismiss()
                        onConfirm()
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            },
        )

        dialog.setContentView(content)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            attributes = attributes.apply { y = dp(12) }
            // 좌우 여백을 줘 카드가 화면 폭에 꽉 차지 않게 한다.
            decorView.setPadding(dp(12), 0, dp(12), 0)
        }
        dialog.show()
    }

    /** 라디오 항목 행. 터치는 행이 받고 라디오 버튼은 상태 표시만 한다 (효과가 행 전체를 덮도록). */
    fun radioRow(label: CharSequence, checked: Boolean): View {
        val radio = RadioButton(activity).apply {
            text = label
            textSize = 17f
            setTextColor(TEXT)
            isChecked = checked
            buttonTintList = ColorStateList.valueOf(ACCENT)
            // RadioButton은 패딩을 무시하고 버튼 드로어블을 뷰 왼쪽 끝에 그리므로
            // 마진으로 카드 안 좌우 여백을 맞춘다.
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(20)
                marginEnd = dp(20)
            }
            setPadding(dp(8), dp(16), dp(12), dp(16))
            isClickable = false
            isFocusable = false
            // 행 pressed 상태가 전파되며 뜨는 기본 원형 리플이 행 하이라이트와 겹치지 않게 없앤다.
            background = null
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(radio)
            addPressEffect(this)
        }
    }

    /** 라디오 행들을 상호 배타로 묶는다. 선택 시 [onSelect]가 호출된다. */
    fun <T> bindRadioGroup(rows: Map<T, View>, onSelect: (T) -> Unit) {
        fun radioOf(row: View): RadioButton =
            row as? RadioButton ?: (row as android.view.ViewGroup).getChildAt(0) as RadioButton
        rows.forEach { (value, row) ->
            row.setOnClickListener {
                rows.values.forEach { radioOf(it).isChecked = it === row }
                onSelect(value)
            }
        }
    }

    companion object {
        private const val ROOT_TAG = "setting_root"
        const val BG = 0xFFF1F2F6.toInt()
        const val CARD = 0xFFFFFFFF.toInt()
        const val TEXT = 0xFF1B1D22.toInt()
        const val SUB_TEXT = 0xFF77808C.toInt()
        const val DIVIDER = 0xFFEAEBEF.toInt()
        const val DIVIDER_STRONG = 0xFFDCDEE3.toInt()
        const val ACCENT = 0xFF3D8BFF.toInt()
    }
}
