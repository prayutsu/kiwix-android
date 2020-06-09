package org.kiwix.kiwixmobile.core.history.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import org.kiwix.kiwixmobile.core.base.SideEffect
import org.kiwix.kiwixmobile.core.dao.HistoryDao
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.ExitActionModeMenu
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.ExitHistory
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.Filter
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.OnItemClick
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.OnItemLongClick
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.UpdateHistory
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.UserClickedConfirmDelete
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.UserClickedDeleteButton
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.UserClickedDeleteSelectedHistoryItems
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.UserClickedShowAllToggle
import org.kiwix.kiwixmobile.core.history.viewmodel.effects.DeleteHistoryItems
import org.kiwix.kiwixmobile.core.history.viewmodel.effects.OpenHistoryItem
import org.kiwix.kiwixmobile.core.history.viewmodel.effects.ShowDeleteHistoryDialog
import org.kiwix.kiwixmobile.core.history.viewmodel.effects.UpdateAllHistoryPreference
import org.kiwix.kiwixmobile.core.reader.ZimReaderContainer
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.Finish
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import javax.inject.Inject

class HistoryViewModel @Inject constructor(
  private val historyDao: HistoryDao,
  private val zimReaderContainer: ZimReaderContainer,
  private val sharedPreferenceUtil: SharedPreferenceUtil
) : ViewModel() {
  val state = MutableLiveData<State>().apply {
    value = State(emptyList(), sharedPreferenceUtil.showHistoryAllBooks, zimReaderContainer.id)
  }
  val effects = PublishProcessor.create<SideEffect<*>>()
  val actions = PublishProcessor.create<Action>()
  private val compositeDisposable = CompositeDisposable()

  init {
    compositeDisposable.addAll(
      viewStateReducer(),
      historyDao.history().subscribeOn(Schedulers.io())
        .subscribe({ actions.offer(UpdateHistory(it)) }, Throwable::printStackTrace)
    )
  }

  private fun viewStateReducer() =
    actions.map { reduce(it, state.value!!) }
      .subscribe(state::postValue, Throwable::printStackTrace)

  private fun reduce(action: Action, state: State): State = when (action) {
    ExitHistory -> finishHistoryActivity(state)
    ExitActionModeMenu -> deselectAllHistoryItems(state)
    UserClickedConfirmDelete -> offerDeletionOfItems(state)
    UserClickedDeleteButton -> offerShowDeleteDialog(state)
    UserClickedDeleteSelectedHistoryItems -> offerShowDeleteDialog(state)
    is UserClickedShowAllToggle -> offerUpdateToShowAllToggle(action, state)
    is OnItemClick -> handleItemClick(state, action)
    is OnItemLongClick -> handleItemLongClick(state, action)
    is Filter -> updateHistoryItemsBasedOnFilter(state, action)
    is UpdateHistory -> updateHistoryList(state, action)
  }

  private fun updateHistoryItemsBasedOnFilter(state: State, action: Filter) =
    state.copy(searchTerm = action.searchTerm)

  private fun updateHistoryList(
    state: State,
    action: UpdateHistory
  ): State = state.copy(historyItems = action.history)

  private fun offerUpdateToShowAllToggle(
    action: UserClickedShowAllToggle,
    state: State
  ): State {
    effects.offer(
      UpdateAllHistoryPreference(
        sharedPreferenceUtil,
        action.isChecked
      )
    )
    return state.copy(showAll = action.isChecked)
  }

  private fun handleItemLongClick(
    state: State,
    action: OnItemLongClick
  ): State = state.toggleSelectionOfItem(action.historyItem)

  private fun handleItemClick(
    state: State,
    action: OnItemClick
  ): State {
    if (state.isInSelectionState) {
      effects.offer(OpenHistoryItem(action.historyItem, zimReaderContainer))
      return state
    }
    return state.toggleSelectionOfItem(action.historyItem)
  }

  private fun offerShowDeleteDialog(state: State): State {
    effects.offer(ShowDeleteHistoryDialog(actions))
    return state
  }

  private fun offerDeletionOfItems(state: State): State {
    effects.offer(DeleteHistoryItems(state, historyDao))
    return state
  }

  private fun deselectAllHistoryItems(state: State): State =
    state.copy(historyItems = state.historyItems.map { it.copy(isSelected = false) })

  private fun finishHistoryActivity(state: State): State {
    effects.offer(Finish)
    return state
  }

  override fun onCleared() {
    compositeDisposable.clear()
    super.onCleared()
  }
}
