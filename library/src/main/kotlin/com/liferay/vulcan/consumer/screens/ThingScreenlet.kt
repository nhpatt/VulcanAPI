/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.vulcan.consumer.screens

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.github.kittinunf.result.failure
import com.liferay.vulcan.consumer.R
import com.liferay.vulcan.consumer.delegates.observe
import com.liferay.vulcan.consumer.extensions.inflate
import com.liferay.vulcan.consumer.fetch
import com.liferay.vulcan.consumer.model.BlogPosting
import com.liferay.vulcan.consumer.model.Collection
import com.liferay.vulcan.consumer.model.Person
import com.liferay.vulcan.consumer.model.Thing
import com.liferay.vulcan.consumer.screens.events.Event
import com.liferay.vulcan.consumer.screens.events.ScreenletEvents
import com.liferay.vulcan.consumer.screens.views.BaseView
import com.liferay.vulcan.consumer.screens.views.Custom
import com.liferay.vulcan.consumer.screens.views.Detail
import com.liferay.vulcan.consumer.screens.views.Row
import com.liferay.vulcan.consumer.screens.views.Scenario
import okhttp3.HttpUrl

open class BaseScreenlet @JvmOverloads constructor(
	context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0) :
	FrameLayout(context, attrs, defStyleAttr) {

	var layout: View? = null
}

class ThingScreenlet @JvmOverloads constructor(
	context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0) :
	BaseScreenlet(context, attrs, defStyleAttr, defStyleRes) {

	var screenletEvents: ScreenletEvents? = null

	var scenario: Scenario = Detail

	companion object {
		val layoutIds: MutableMap<String, MutableMap<Scenario, Int>> = mutableMapOf(
			"BlogPosting" to BlogPosting.DEFAULT_VIEWS,
			"Collection" to Collection.DEFAULT_VIEWS,
			"Person" to Person.DEFAULT_VIEWS
		)
	}

	val layoutId: Int

	var thing: Thing? by observe {
		val layoutId = getLayoutIdFor(thing = it) ?: R.layout.thing_default

		layout?.also {
			baseView?.onDestroy()
			this.removeView(it)
		}

		layout = this.inflate(layoutId)

		addView(layout)

		baseView?.apply {
			screenlet = this@ThingScreenlet
			thing = it
		}
	}

	val baseView: BaseView? get() = layout as? BaseView

	fun load(thingId: String, credentials: String? = null, scenario: Scenario? = null,
		onComplete: ((ThingScreenlet) -> Unit)? = null) {
		HttpUrl.parse(thingId)?.let {
			fetch(it, credentials) {
				if (scenario != null) {
					this.scenario = scenario
				}

				thing = it.component1()

				it.failure { baseView?.showError(it.message) }

				onComplete?.invoke(this)
			}
		}

	}

	private fun getLayoutIdFor(thing: Thing?): Int? {
		if (layoutId != 0) return layoutId

		return thing?.let {
			onEventFor(Event.FetchLayout(thing = it, scenario = scenario))
		}
	}

	init {
		val typedArray = attrs?.let { context.theme.obtainStyledAttributes(it, R.styleable.ThingScreenlet, 0, 0) }

		layoutId = typedArray?.getResourceId(R.styleable.ThingScreenlet_layoutId, 0) ?: 0

		val scenarioId = typedArray?.getString(R.styleable.ThingScreenlet_scenario) ?: ""

		scenario = Scenario.stringToScenario?.invoke(scenarioId) ?: when (scenarioId.toLowerCase()) {
			"detail", "" -> Detail
			"row" -> Row
			else -> Custom(scenarioId)
		}
	}

	@Suppress("UNCHECKED_CAST")
	fun <T> onEventFor(event: Event<T>): T? = when (event) {
		is Event.Click -> screenletEvents?.onClickEvent(layout as BaseView, event.view, event.thing) as? T
		is Event.FetchLayout -> {
			(screenletEvents?.onGetCustomLayout(this, event.view, event.thing, event.scenario) ?:
				layoutIds[event.thing.type[0]]?.get(event.scenario)) as? T
		}
	}
}
