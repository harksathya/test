package com.hark.sothudiary.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hark.sothudiary.PlatformActions
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.domain.pricing.Verdict

/** Everything a screen needs, passed down as one object. */
class AppContext(
    val repo: JsonRepository,
    val nav: Nav,
    val platform: PlatformActions,
    val filters: FilterState,
    val toggleLang: () -> Unit,
    val wide: Boolean,
)

enum class SortBy { NEWEST, PRICE_LOW, PRICE_HIGH, PER_CENT, FOLLOW_UP }

enum class GroupBy { NONE, SOURCE, LOCATION, TYPE, STATUS }

enum class ListMode { LIST, FOLDERS }

/** Search / filter / sort state for the property list, kept while navigating. */
class FilterState {
    var query by mutableStateOf("")
    var types by mutableStateOf(setOf<PropertyType>())
    var statuses by mutableStateOf(setOf<DealStatus>())
    var verdicts by mutableStateOf(setOf<Verdict>())
    var minPrice by mutableStateOf("")
    var maxPrice by mutableStateOf("")
    var sort by mutableStateOf(SortBy.NEWEST)
    var showFilters by mutableStateOf(false)
    var group by mutableStateOf(GroupBy.NONE)
    var mode by mutableStateOf(ListMode.LIST)
    var collapsed by mutableStateOf(setOf<String>())

    fun activeCount(): Int = types.size + statuses.size + verdicts.size +
        (if (minPrice.isNotBlank()) 1 else 0) + (if (maxPrice.isNotBlank()) 1 else 0)

    fun clear() {
        types = emptySet(); statuses = emptySet(); verdicts = emptySet(); minPrice = ""; maxPrice = ""
    }
}
