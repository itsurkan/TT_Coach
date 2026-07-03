package com.ttcoachai.shared.models

/** Playing hand. [baselineString] matches PersonalBaseline.drillerHandedness values. */
enum class Handedness(val baselineString: String) {
    RIGHT("right"),
    LEFT("left")
}
