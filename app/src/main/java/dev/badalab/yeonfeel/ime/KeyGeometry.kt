package dev.badalab.yeonfeel.ime

import android.graphics.RectF

/**
 * 키 행 정의를 화면 사각형으로 배치하는 계산기.
 * KeyboardView(실제 입력)와 타점 시각화가 이 코드를 공유하므로,
 * 시각화가 재구성하는 자판이 실제 배치와 어긋나지 않는다.
 */
object KeyGeometry {

    data class Placed(val key: Key, val rect: RectF)

    /** 숫자 열은 다른 열보다 살짝 낮게 그린다. */
    const val NUMBER_ROW_HEIGHT_WEIGHT = 0.85f

    /** 분할 배치에서 양쪽 가장자리에 두는 여백 (전체 폭 비율). */
    const val SPLIT_SIDE_MARGIN_RATIO = 0.03f

    fun place(
        rows: List<List<Key>>,
        width: Float,
        height: Float,
        gapX: Float,
        gapY: Float,
        compactNumberRow: Boolean,
        split: Boolean,
        splitGapRatio: Float,
    ): List<Placed> {
        val heightWeights = FloatArray(rows.size) { 1f }
        if (compactNumberRow && rows.isNotEmpty()) heightWeights[0] = NUMBER_ROW_HEIGHT_WEIGHT
        val unit = height / heightWeights.sum()
        val placed = mutableListOf<Placed>()
        val spaceLeftEdge = if (split) maxLeftBlockEnd(rows, width, splitGapRatio) else null
        var top = 0f
        rows.forEachIndexed { rowIdx, row ->
            val rowHeight = unit * heightWeights[rowIdx]
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            if (split) {
                placeSplitRow(
                    row, totalWeight, top, rowHeight, gapX, gapY,
                    width, splitGapRatio, spaceLeftEdge, placed,
                )
            } else {
                var x = 0f
                row.forEach { key ->
                    val keyWidth = width * (key.widthWeight / totalWeight)
                    placed += Placed(
                        key,
                        RectF(x + gapX, top + gapY, x + keyWidth - gapX, top + rowHeight - gapY),
                    )
                    x += keyWidth
                }
            }
            top += rowHeight
        }
        return placed
    }

    /**
     * 분할 배치: 행을 가중치 절반 지점에서 좌우로 나누고 중앙에 간격을 둔다.
     * 경계에 걸친 스페이스바는 반으로 갈라 양쪽에 배치하되, 왼쪽 조각의
     * 오른끝만 [spaceLeftEdge]까지 늘려 윗 행 키 끝(ㅍ)과 맞춘다.
     */
    private fun placeSplitRow(
        row: List<Key>,
        totalWeight: Float,
        top: Float,
        rowHeight: Float,
        gapX: Float,
        gapY: Float,
        width: Float,
        splitGapRatio: Float,
        spaceLeftEdge: Float?,
        placed: MutableList<Placed>,
    ) {
        val sideMargin = width * SPLIT_SIDE_MARGIN_RATIO
        val usable = width - sideMargin * 2
        val unit = usable / (totalWeight * (1f + splitGapRatio))
        val gapWidth = usable - totalWeight * unit
        val half = totalWeight / 2f
        var acc = 0f
        var x = sideMargin
        var gapPlaced = false
        row.forEach { key ->
            val w = key.widthWeight
            val straddles = acc < half && acc + w > half
            if (straddles && key.type == KeyType.SPACE) {
                val leftWidth = (half - acc) * unit
                val leftEnd = maxOf(x + leftWidth, spaceLeftEdge ?: 0f)
                placed += Placed(
                    key,
                    RectF(x + gapX, top + gapY, leftEnd - gapX, top + rowHeight - gapY),
                )
                x += leftWidth + gapWidth
                val rightWidth = (acc + w - half) * unit
                placed += Placed(
                    key,
                    RectF(x + gapX, top + gapY, x + rightWidth - gapX, top + rowHeight - gapY),
                )
                x += rightWidth
                gapPlaced = true
            } else {
                val keyWidth = w * unit
                placed += Placed(
                    key,
                    RectF(x + gapX, top + gapY, x + keyWidth - gapX, top + rowHeight - gapY),
                )
                x += keyWidth
            }
            acc += w
            if (!gapPlaced && acc >= half) {
                x += gapWidth
                gapPlaced = true
            }
        }
    }

    /** 분할된 스페이스바가 없는 행들의 좌 블록 오른끝 최댓값 — 스페이스바 끝 정렬 기준. */
    private fun maxLeftBlockEnd(
        rows: List<List<Key>>,
        width: Float,
        splitGapRatio: Float,
    ): Float? {
        val sideMargin = width * SPLIT_SIDE_MARGIN_RATIO
        val usable = width - sideMargin * 2
        var best: Float? = null
        rows.forEach { row ->
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val half = totalWeight / 2f
            var acc = 0f
            var leftWeight = 0f
            var hasSplitSpace = false
            row.forEach { key ->
                val w = key.widthWeight
                if (acc < half && acc + w > half && key.type == KeyType.SPACE) {
                    hasSplitSpace = true
                } else if (acc < half) {
                    leftWeight += w
                }
                acc += w
            }
            if (!hasSplitSpace) {
                val unit = usable / (totalWeight * (1f + splitGapRatio))
                val end = sideMargin + leftWeight * unit
                if (best == null || end > best!!) best = end
            }
        }
        return best
    }
}
