/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package tachiyomi.ui.catalogs

import androidx.core.os.LocaleListCompat
import com.freeletics.rxredux.StateAccessor
import com.freeletics.rxredux.reduxStore
import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.ofType
import tachiyomi.core.rx.RxSchedulers
import tachiyomi.core.rx.addTo
import tachiyomi.domain.catalog.model.Catalog
import tachiyomi.domain.catalog.repository.CatalogRepository
import tachiyomi.ui.base.BasePresenter
import javax.inject.Inject

class CatalogsPresenter @Inject constructor(
  private val catalogRepository: CatalogRepository,
  private val schedulers: RxSchedulers
) : BasePresenter() {

  private val actions = PublishRelay.create<Action>()

  val state = BehaviorRelay.create<CatalogsViewState>()

  init {
    actions
      .observeOn(schedulers.io)
      .reduxStore(
        initialState = CatalogsViewState(),
        sideEffects = listOf(::loadCatalogsSideEffect),
        reducer = ::reduce
      )
      .distinctUntilChanged()
      .observeOn(schedulers.main)
      .subscribe(state::accept)
      .addTo(disposables)
  }

  @Suppress("unused_parameter")
  private fun loadCatalogsSideEffect(
    actions: Observable<Action>,
    stateFn: StateAccessor<CatalogsViewState>
  ): Observable<Action> {
    val internalCatalogs = catalogRepository.getInternalCatalogsFlowable()
      .observeOn(schedulers.io)
      .toObservable()

    val installedCatalogs = catalogRepository.getInstalledCatalogsFlowable()
      .observeOn(schedulers.io)
      .toObservable()

    val availableCatalogs = catalogRepository.getAvailableCatalogsFlowable()
      .observeOn(schedulers.io)
      .toObservable()

    val selectedLanguage = actions.ofType<Action.SetLanguageChoice>()
      .observeOn(schedulers.io)
      .map { it.choice }
      .startWith(stateFn().languageChoice)

    return Observables.combineLatest(
      internalCatalogs,
      installedCatalogs,
      availableCatalogs,
      selectedLanguage
    ) { internal, installed, available, choice ->
      val items = mutableListOf<Any>()
      items.addAll(installed)
      items.addAll(internal)

      if (available.isNotEmpty()) {
        val choices = LanguageChoices(getLanguageChoices(available), choice)
        val availableCatalogsFiltered = getFilteredAvailableCatalogs(available, choice)

        items.add(choices)
        items.addAll(availableCatalogsFiltered)
      }

      items
    }.map(Action::ItemsUpdate)

  }

  private fun getLanguageChoices(catalogs: List<Catalog.Available>): List<LanguageChoice> {
    val knownLanguages = mutableListOf<LanguageChoice.One>()
    val unknownLanguages = mutableListOf<Language>()

    catalogs.asSequence()
      .map { Language(it.lang) }
      .distinct()
      .sortedWith(UserLanguagesComparator())
      .forEach { code ->
        if (code.toEmoji() != null) {
          knownLanguages.add(LanguageChoice.One(code))
        } else {
          unknownLanguages.add(code)
        }
      }

    val languages = mutableListOf<LanguageChoice>()
    languages.add(LanguageChoice.All)
    languages.addAll(knownLanguages)
    languages.add(LanguageChoice.Others(unknownLanguages))

    return languages
  }

  private fun getFilteredAvailableCatalogs(
    catalogs: List<Catalog.Available>,
    choice: LanguageChoice
  ): List<Catalog.Available> {
    return when (choice) {
      is LanguageChoice.All -> catalogs
      is LanguageChoice.One -> catalogs.filter { choice.language.code == it.lang }
      is LanguageChoice.Others -> {
        val codes = choice.languages.map { it.code }
        catalogs.filter { it.lang in codes }
      }
    }
  }

  fun setLanguageChoice(languageChoice: LanguageChoice) {
    actions.accept(Action.SetLanguageChoice(languageChoice))
  }

}

private class UserLanguagesComparator : Comparator<Language> {

  private val userLanguages = mutableSetOf<String>()

  init {
    val userLocales = LocaleListCompat.getDefault()
    for (i in 0 until userLocales.size()) {
      userLanguages.add(userLocales[i].language)
    }
  }

  override fun compare(langOne: Language, langTwo: Language): Int {
    val langOnePosition = userLanguages.indexOf(langOne.code)
    val langTwoPosition = userLanguages.indexOf(langTwo.code)

    return when {
      langOnePosition != -1 && langTwoPosition != -1 -> langOnePosition.compareTo(langTwoPosition)
      langOnePosition != -1 -> -1
      langTwoPosition != -1 -> 1
      else -> langOne.code.compareTo(langTwo.code)
    }
  }

}

private sealed class Action {
  data class ItemsUpdate(val items: List<Any>) : Action()
  data class SetLanguageChoice(val choice: LanguageChoice) : Action()
}

private fun reduce(state: CatalogsViewState, action: Action): CatalogsViewState {
  return when (action) {
    is Action.ItemsUpdate -> state.copy(items = action.items)
    is Action.SetLanguageChoice -> state.copy(languageChoice = action.choice)
  }
}