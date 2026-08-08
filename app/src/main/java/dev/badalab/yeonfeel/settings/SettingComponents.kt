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

    private val column = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, dp(12), dp(24))
    }

    fun root(): View = ScrollView(activity).apply {
        fitsSystemWindows = true
        setBackgroundColor(BG)
        addView(column)
    }

    fun header(title: String, showBack: Boolean = true) {
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
        row.addView(TextView(activity).apply {
            text = title
            textSize = 26f
            setTextColor(TEXT)
            setTypeface(null, Typeface.BOLD)
        })
        column.addView(row)
    }

    fun caption(text: String) {
        column.addView(TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(SUB_TEXT)
            setPadding(dp(20), dp(4), dp(20), dp(10))
        })
    }

    fun card(vararg rows: View) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD)
                cornerRadius = dp(24).toFloat()
            }
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
        onClick?.let { handler -> row.setOnClickListener { handler() } }
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

    /** 라벨 + 슬라이더 행. 값이 바뀔 때마다 [onChange]가 호출된다. */
    fun sliderRow(label: String, max: Int, initial: Int, onChange: (Int) -> Unit): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        row.addView(TextView(activity).apply {
            text = label
            textSize = 17f
            setTextColor(TEXT)
        })
        row.addView(android.widget.SeekBar(activity).apply {
            this.max = max
            progress = initial
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(ACCENT)
            // 좌우 패딩이 썸(원) 반지름보다 작으면 양 끝에서 썸이 잘린다.
            setPadding(dp(16), dp(10), dp(16), dp(4))
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) onChange(value)
                }

                override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
            })
        })
        return row
    }

    fun radioRow(label: String, checked: Boolean): RadioButton = RadioButton(activity).apply {
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
    }

    /** 라디오 버튼들을 상호 배타로 묶는다. 선택 시 [onSelect]가 호출된다. */
    fun <T> bindRadioGroup(radios: Map<T, RadioButton>, onSelect: (T) -> Unit) {
        radios.forEach { (value, radio) ->
            radio.setOnClickListener {
                radios.values.forEach { it.isChecked = it === radio }
                onSelect(value)
            }
        }
    }

    companion object {
        const val BG = 0xFFF1F2F6.toInt()
        const val CARD = 0xFFFFFFFF.toInt()
        const val TEXT = 0xFF1B1D22.toInt()
        const val SUB_TEXT = 0xFF77808C.toInt()
        const val DIVIDER = 0xFFEAEBEF.toInt()
        const val DIVIDER_STRONG = 0xFFDCDEE3.toInt()
        const val ACCENT = 0xFF3D8BFF.toInt()
    }
}
