package com.lrlr.carmigo.core

/** 표시할 때만 구형 문구를 읽어 바꾼다. 저장된 원문과 카드 식별자는 유지한다. */
object MomentText {
    const val COLD_TITLE = "영하에서 달린 첫날"
    const val HEAT_TITLE = "33℃ 이상에서 달린 첫날"
    const val COLD_WHY = "기록을 시작한 뒤, 그해 겨울 처음으로 주행 종료 시 차가 잰 바깥 기온이 영하였습니다."
    const val HEAT_WHY = "기록을 시작한 뒤, 그해 처음으로 주행 종료 시 차가 잰 바깥 기온이 33℃ 이상이었습니다."
    const val LONG_WHY = "기록을 시작한 뒤 한 번의 주행으로 처음 300km 이상을 달렸습니다. 거리는 출발·종료 시 계기판 차이입니다."

    fun title(moment: Moment): String = when {
        moment.kind == MomentKind.SEASON && moment.id.startsWith("cold-") -> COLD_TITLE
        moment.kind == MomentKind.SEASON && moment.id.startsWith("heat-") -> HEAT_TITLE
        moment.kind == MomentKind.PEOPLE && moment.id.startsWith("companion-") ->
            moment.id.removePrefix("companion-").toIntOrNull()?.let { "옆자리에 누군가 탄 ${it}번째 날" } ?: moment.title
        else -> moment.title
    }

    fun explanation(moment: Moment): String = when {
        moment.kind == MomentKind.SEASON && moment.id.startsWith("cold-") -> COLD_WHY
        moment.kind == MomentKind.SEASON && moment.id.startsWith("heat-") -> HEAT_WHY
        moment.kind == MomentKind.SEASON && moment.id.startsWith("newyear-") ->
            moment.id.removePrefix("newyear-").toIntOrNull()?.let { "기록을 시작한 뒤, ${it}년에 처음 마친 주행입니다." } ?: moment.why
        moment.kind == MomentKind.DISTANCE && moment.id == "long-first" -> LONG_WHY
        else -> moment.why
    }
}
